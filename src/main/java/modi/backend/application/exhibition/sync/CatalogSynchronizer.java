package modi.backend.application.exhibition.sync;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import modi.backend.application.exhibition.sync.enricher.DetailTargetState;
import modi.backend.application.exhibition.sync.outbox.ExhibitionOutboxFacade;
import modi.backend.domain.exhibition.sync.SyncTrigger;
import modi.backend.domain.exhibition.sync.data.CatalogDetailData;
import modi.backend.domain.exhibition.sync.data.CatalogExhibitionData;
import modi.backend.domain.exhibition.sync.data.CatalogListData;
import modi.backend.domain.exhibition.sync.entity.SyncRun;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessageType;
import modi.backend.domain.exhibition.sync.port.ExhibitionCatalogClient;

/**
 * 외부 전시 API 수집 루프(목록+상세 한 패스) — <b>트랜잭션 밖 조율자</b>다(enricher와 동형).
 *
 * <p>루프와 외부 호출(목록·상세 조회)은 트랜잭션 없이 돌고, 영속 단계만 {@link ExhibitionSyncFacade}의
 * 트랜잭션 메서드(프록시 경유)로 위임한다 — 외부 호출을 트랜잭션에 물지 않는 원칙(커넥션 장기 점유 방지)과,
 * 신규 1건의 [전시장+전시+상세+아웃박스 enqueue] <b>원자성</b>(ADR-10)을 동시에 지키기 위한 분리다
 * (예전엔 이 루프가 파사드 안에 있어 자기호출로는 원자 트랜잭션을 만들 수 없었다).
 *
 * <p>신규는 전시장을 resolve-or-create해 상세까지 채워 적재하고, 기존 미완성은 상세만 채운다.
 * 상세 조회가 일시 실패하면 아무것도 적재하지 않고 FETCH_DETAIL 메시지만 남겨 이 행을 다음 주기로 연기한다.
 */
@Component
@RequiredArgsConstructor
public class CatalogSynchronizer {

	private static final Logger log = LoggerFactory.getLogger(CatalogSynchronizer.class);

	private final ExhibitionSyncFacade exhibitionSyncFacade;
	private final ExhibitionOutboxFacade exhibitionOutboxFacade;
	private final ExhibitionCatalogClient catalogClient;

	/**
	 * 정기(SCHEDULE) 동기화.
	 *
	 * @return 이번 동기화로 새로 적재된 전시 수(기존 행 상세 완성 건은 제외)
	 */
	public int syncCatalog() {
		return syncCatalog(SyncTrigger.SCHEDULE);
	}

	/** 계기(BOOT/SCHEDULE/MANUAL)를 명시한 동기화 — sync_run.trigger_type에 남긴다("왜 이 시각에 돌았나"). */
	public int syncCatalog(SyncTrigger trigger) {
		// 배치 전체가 같은 last_seen_at을 공유해야 "이번 동기화에 안 보인 행"(last_seen_at < 이 시각)이 한 번에 가려진다.
		// 아이템마다 now()를 찍으면 그 경계가 흐려진다.
		LocalDateTime syncedAt = LocalDateTime.now();
		SyncRun run = SyncRun.started(trigger, syncedAt);
		CatalogListData fetched = catalogClient.fetchAll();
		List<CatalogExhibitionData> collected = fetched.items();
		run.fetched(fetched.totalCount(), fetched.truncated(), collected.size());
		if (fetched.truncated()) {
			log.warn("전시 동기화 절단 — 원천 총 {}건 중 상한(max-pages × num-of-rows)에 걸려 일부만 수집됨",
					fetched.totalCount());
		}
		int inserted = 0;
		int completed = 0;
		int skipped = 0;
		int deferred = 0;
		for (CatalogExhibitionData data : collected) {
			exhibitionSyncFacade.archiveListResponse(data, syncedAt);
			if (!hasValidPeriod(data)) {
				skipped++;
				continue;
			}
			try {
				switch (syncOne(data)) {
					case INSERTED -> inserted++;
					case COMPLETED -> completed++;
					case SKIPPED -> { /* 이미 완성된 행 — 변화 없음 */ }
				}
			} catch (RuntimeException e) {
				deferred++;
				log.warn("전시 동기화 단건 실패(externalId={}, 다음 주기 재시도): {}", data.externalId(), e.getMessage());
			}
		}
		if (skipped > 0 || completed > 0 || deferred > 0) {
			log.info("전시 동기화: 수집 {} / 신규적재 {} / 기존상세완성 {} / 기간스킵 {} / 실패연기 {}",
					collected.size(), inserted, completed, skipped, deferred);
		}
		exhibitionSyncFacade.archiveSyncRun(run, inserted, completed, skipped, deferred);
		return inserted;
	}

	private enum SyncOutcome {
		INSERTED, COMPLETED, SKIPPED
	}

	/**
	 * 목록 1건을 상세까지 채워 적재/완성한다. 상세 조회(외부 호출)는 여기(트랜잭션 밖)서 마치고, 영속은 파사드
	 * 트랜잭션 메서드에 값만 넘긴다. 기존 미완성 행의 상세 반영은 FETCH_DETAIL 메시지 처리와 같은 경로
	 * ({@code applyDetailForJob})를 탄다 — "상세가 도착했다"의 반영 의미론을 한 곳으로 모은다.
	 */
	private SyncOutcome syncOne(CatalogExhibitionData data) {
		DetailTargetState state = exhibitionSyncFacade.findDetailTargetState(data.externalId());
		if (state == DetailTargetState.ALREADY_SYNCED) {
			return SyncOutcome.SKIPPED;
		}
		// 상세를 <b>먼저</b> 받아본다 — 일시 실패하면 아무것도 적재하지 않고 이 행만 다음 주기로 연기한다
		// (불완전한 행·전시장만 남기지 않는다). 상세가 성공/빈 응답일 때만 영속 단계로 진행한다.
		Optional<CatalogDetailData> detail = fetchDetailDeferring(data.externalId());
		if (state == DetailTargetState.NEEDS_DETAIL) {
			// 상세 원본 벤더층 적재는 applyDetailForJob이 반영과 함께 수행한다(중복 적재 없음).
			detail.ifPresentOrElse(d -> exhibitionSyncFacade.applyDetailForJob(data.externalId(), d),
					() -> exhibitionSyncFacade.markDetailCheckedForJob(data.externalId()));
			return SyncOutcome.COMPLETED;
		}
		exhibitionSyncFacade.applyNewListing(data, detail.orElse(null)); // 한 트랜잭션(ADR-10)
		exhibitionSyncFacade.archiveDetailOutcome(data.externalId(), detail.orElse(null));
		return SyncOutcome.INSERTED;
	}

	/** 상세를 조회한다. 일시 실패면 FETCH_DETAIL 메시지를 남기고 예외를 전파해 호출부가 이 행만 연기하게 한다. */
	private Optional<CatalogDetailData> fetchDetailDeferring(String externalId) {
		try {
			return catalogClient.fetchDetail(externalId);
		} catch (RuntimeException e) {
			// 진행 상태(재시도)는 아웃박스가 안다 — 상세 실패는 FETCH_DETAIL 메시지로 남겨 백오프 재시도되게 한다.
			// 실패 경로라 지킬 상태 변경이 없다 — enqueue만 독립 트랜잭션으로 남기고(best-effort), 예외는 전파한다.
			enqueueDetailRetryBestEffort(externalId);
			throw e;
		}
	}

	/** 상세 재시도 메시지 enqueue — 실패해도 동기화를 깨지 않는다(다음 sync가 같은 행을 다시 만난다). */
	private void enqueueDetailRetryBestEffort(String externalId) {
		try {
			exhibitionOutboxFacade.enqueue(OutboxMessageType.FETCH_DETAIL, externalId, LocalDateTime.now());
		} catch (RuntimeException e) {
			log.warn("상세 재시도 메시지 enqueue 실패(externalId={}, 동기화는 계속): {}", externalId, e.getMessage());
		}
	}

	private static boolean hasValidPeriod(CatalogExhibitionData data) {
		return data.startDate() == null || data.endDate() == null || !data.startDate().isAfter(data.endDate());
	}
}
