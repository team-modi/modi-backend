package modi.backend.ingestion.application.enricher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import modi.backend.ingestion.application.ExhibitionSyncFacade;
import modi.backend.ingestion.application.draft.ExhibitionDraftFacade;
import modi.backend.ingestion.application.outbox.ExhibitionOutboxFacade;
import modi.backend.ingestion.application.outbox.OutboxFailures;
import modi.backend.ingestion.application.outbox.OutboxProcessing;
import modi.backend.ingestion.config.CatalogEnrichProperties;
import modi.backend.ingestion.domain.data.GenreClassification;
import modi.backend.ingestion.domain.data.GenreResult;
import modi.backend.ingestion.domain.outbox.OutboxFailureType;
import modi.backend.ingestion.domain.outbox.OutboxMessage;
import modi.backend.ingestion.domain.outbox.OutboxMessageType;
import modi.backend.ingestion.domain.port.GenreClassificationException;
import modi.backend.ingestion.domain.port.GenreClassifier;

/**
 * 장르 분류 처리기 — <b>전시 아웃박스 기반</b>({@link OutboxMessageType#CLASSIFY_GENRE}).
 *
 * <p>대상 해소는 <b>draft 우선, 전시 폴백</b> 이원화다(ADR-10): 신규 유입은 draft에 분류를 반영하고 게이트를
 * 채우면 그 트랜잭션에서 승격까지 간다. 레거시(이미 승격됐지만 미분류) 전시는 기존처럼 정준층에 직접 쓴다.
 * 미분류 레거시의 발견은 스윕(멱등 enqueue)이, 신규 draft의 분류 메시지는 상세 해소가 건다(스텝 체인).
 *
 * <p><b>계약 반전(ADR-11)</b>: 분류기는 실패 시 폴백값 대신 {@link GenreClassificationException}을 던진다 —
 * 배치의 미처리 대상 전부를 RETRYABLE로 남기고(장르는 시도 소진 없는 무기한 정책), 회복 후 릴레이가 다시 집는다.
 * "AI는 늦어도 최소 1회 무조건"이 폴백값이 아니라 아웃박스 상태로 보인다.
 *
 * <p>흐름: (1) 스윕 — 미분류 레거시 CATALOG를 멱등 enqueue. (2) 드레인 — 도래 메시지를 배치로(배치당 AI 1콜)
 * 분류하고 대상별(draft/전시)로 반영한다. AI 호출은 트랜잭션 밖이다.
 */
@Component
@RequiredArgsConstructor
public class CatalogEnricher {

	private static final Logger log = LoggerFactory.getLogger(CatalogEnricher.class);

	private final ExhibitionSyncFacade exhibitionSyncFacade;
	private final ExhibitionDraftFacade exhibitionDraftFacade;
	private final ExhibitionOutboxFacade exhibitionOutboxFacade;
	private final CatalogEnrichProperties properties;
	/** 장르 분류 전략(AI 체인/mock) — 주입되는 구현은 {@code app.exhibition.genre.classifier}로 선택된다(@Primary). */
	private final GenreClassifier genreClassifier;

	/**
	 * 미분류 레거시를 스윕하고 도래한 장르 메시지를 드레인한다. 각 배치는 AI 1회 호출(배치가 비면 조기 종료).
	 *
	 * @return 이번 실행에서 상태를 전이시킨 CLASSIFY_GENRE 메시지 수(분류·재시도·마감)
	 */
	public int enrichGenres() {
		LocalDateTime now = LocalDateTime.now();
		sweepUnclassified(now);
		int total = 0;
		for (int i = 0; i < properties.genreMaxBatchesPerRun(); i++) {
			int processed = drainBatch(properties.genreBatchSize());
			total += processed;
			if (processed == 0) {
				break; // 도래 메시지 소진 → 조기 종료(AI를 빈 배치로 태우지 않음)
			}
		}
		if (total > 0) {
			log.info("전시 장르 메시지 처리 {}건", total);
		}
		return total;
	}

	/** 미분류 레거시 CATALOG를 멱등 enqueue한다(이번 실행이 드레인할 수 있는 만큼만 — 나머진 다음 실행이 스윕). */
	private void sweepUnclassified(LocalDateTime now) {
		int sweepLimit = properties.genreBatchSize() * properties.genreMaxBatchesPerRun();
		List<String> externalIds = exhibitionSyncFacade.findUnclassifiedCatalogExternalIds(sweepLimit);
		if (!externalIds.isEmpty()) {
			exhibitionOutboxFacade.enqueueAll(OutboxMessageType.CLASSIFY_GENRE, externalIds, now);
		}
	}

	/** 분류 대상 1건 — 메시지와 (draft/전시) 해소 결과의 짝. */
	private record GenreTargetResolution(OutboxMessage message, GenreClassification input, boolean isDraft) {
	}

	/**
	 * 한 배치 드레인: 도래 메시지 조회 → [조회 tx] 대상 해소(draft 우선·전시 폴백) → <b>tx 밖 AI 호출</b> →
	 * [반영 tx] 대상별 반영 + 메시지 전이. AI 호출이 트랜잭션 밖이라는 것이 이 배치의 존재 이유다
	 * (배치 1콜이 최대 60초까지 걸린다).
	 *
	 * @return 이 배치에서 상태를 전이시킨 메시지 수(0이면 도래 메시지 없음)
	 */
	private int drainBatch(int batchSize) {
		LocalDateTime now = LocalDateTime.now();
		List<OutboxMessage> messages = exhibitionOutboxFacade.findDue(OutboxMessageType.CLASSIFY_GENRE, batchSize, now);
		if (messages.isEmpty()) {
			return 0;
		}
		List<String> externalIds = messages.stream().map(OutboxMessage::getTargetKey).toList();
		Map<String, GenreClassification> exhibitionInputs = exhibitionSyncFacade.resolveGenreInputs(externalIds);

		List<GenreTargetResolution> actionable = new ArrayList<>();
		int transitioned = 0;
		for (OutboxMessage message : messages) {
			String externalId = message.getTargetKey();
			GenreClassification draftInput = exhibitionDraftFacade.resolveGenreInput(externalId).orElse(null);
			if (draftInput != null) {
				actionable.add(new GenreTargetResolution(message, draftInput, true));
				continue;
			}
			GenreClassification exhibitionInput = exhibitionInputs.get(externalId);
			if (exhibitionInput != null) {
				actionable.add(new GenreTargetResolution(message, exhibitionInput, false));
				continue;
			}
			// draft도 전시도 분류 대상이 아니다(이미 분류됐거나 사라짐) — 할 일 없으니 성공으로 마감.
			if (OutboxProcessing.succeed(exhibitionOutboxFacade, message, now)) {
				transitioned++;
			}
		}
		if (actionable.isEmpty()) {
			return transitioned;
		}

		List<GenreResult> results;
		try {
			results = genreClassifier.classifyAll(actionable.stream().map(GenreTargetResolution::input).toList());
		} catch (GenreClassificationException e) {
			// 전 공급자 실패(ADR-11) — 배치 전부를 RETRYABLE로 남긴다(장르는 시도 소진 없는 무기한 정책).
			// draft는 분류될 때까지 승격 대기(사용자 확정 감수).
			log.warn("장르 분류 배치 실패({}건) — 회복 후 재시도: {}", actionable.size(), e.getMessage());
			for (GenreTargetResolution target : actionable) {
				if (OutboxProcessing.fail(exhibitionOutboxFacade, target.message(), OutboxFailureType.RETRYABLE,
						OutboxFailures.describe(e), now)) {
					transitioned++;
				}
			}
			return transitioned;
		}

		for (int i = 0; i < actionable.size(); i++) {
			GenreTargetResolution target = actionable.get(i);
			GenreResult result = results.get(i);
			try {
				if (target.isDraft()) {
					// 게이트를 채우면 이 반영 트랜잭션에서 승격까지 간다(ADR-10 승격 경계).
					exhibitionDraftFacade.applyGenreAndPromote(target.message().getTargetKey(), result, now);
				} else {
					exhibitionSyncFacade.applyGenreResults(Map.of(target.message().getTargetKey(), result), now);
				}
			} catch (OptimisticLockingFailureException e) {
				continue; // 반영 중 충돌 — 다른 워커가 처리(메시지는 그 워커가 마감한다).
			} catch (RuntimeException e) {
				// 장르는 PERMANENT로 굳히지 않는다(무기한 정책의 취지 — ADR-11). 반영 예외를 classify하면
				// cause 체인의 IllegalArgumentException 등이 PERMANENT로 분류돼 draft가 조용히 영구 미승격으로
				// 남는 비대칭이 생긴다(상세와 달리 장르엔 draft FAILED 연동이 없다) — RETRYABLE 고정으로 막는다.
				if (OutboxProcessing.fail(exhibitionOutboxFacade, target.message(), OutboxFailureType.RETRYABLE,
						OutboxFailures.describe(e), now)) {
					transitioned++;
				}
				continue;
			}
			if (OutboxProcessing.succeed(exhibitionOutboxFacade, target.message(), now)) {
				transitioned++;
			}
		}
		return transitioned;
	}
}
