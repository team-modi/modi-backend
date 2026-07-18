package modi.backend.application.exhibition.sync;

import modi.backend.application.exhibition.sync.enricher.DetailTargetState;
import modi.backend.application.exhibition.sync.enricher.GenreTarget;
import modi.backend.application.exhibition.sync.enricher.PlaceHoursTarget;
import modi.backend.application.exhibition.sync.job.EnrichmentJobFacade;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.catalog.Exhibition;
import modi.backend.domain.exhibition.catalog.ExhibitionGenre;
import modi.backend.domain.exhibition.catalog.ExhibitionPlace;
import modi.backend.domain.exhibition.catalog.ExhibitionPlaceRepository;
import modi.backend.domain.exhibition.catalog.ExhibitionRepository;
import modi.backend.domain.exhibition.enrichment.JobType;
import modi.backend.domain.exhibition.sync.data.GenreClassification;
import modi.backend.domain.exhibition.sync.data.GenreResult;
import modi.backend.domain.exhibition.sync.entity.GooglePlaceResponse;
import modi.backend.domain.exhibition.sync.data.PlaceHoursData;
import modi.backend.domain.exhibition.hours.PlaceHoursStatus;
import modi.backend.domain.exhibition.hours.PlaceHoursVendor;
import modi.backend.domain.exhibition.sync.data.CatalogDetailData;
import modi.backend.domain.exhibition.sync.data.CatalogExhibitionData;
import modi.backend.domain.exhibition.sync.data.CatalogListData;
import modi.backend.domain.exhibition.sync.entity.CultureDetailResponse;
import modi.backend.domain.exhibition.sync.entity.CultureListResponse;
import modi.backend.domain.exhibition.sync.port.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.sync.entity.SyncRun;
import modi.backend.domain.exhibition.sync.port.SyncRunRepository;
import modi.backend.domain.exhibition.sync.SyncTrigger;
import modi.backend.infra.exhibition.sync.GooglePlaceResponseJpaRepository;
import modi.backend.infra.exhibition.sync.CultureDetailResponseJpaRepository;
import modi.backend.infra.exhibition.sync.CultureListResponseJpaRepository;

/**
 * 전시 수집·보강 파이프라인 조율(03_전시.md) — 외부 카탈로그 동기화(syncCatalog)와 통합 작업큐 처리기(enricher)의
 * DB 경계를 맡는다. 사용자 대면 유스케이스는 {@code serving.ExhibitionFacade}가 따로 담당한다: 조회 API와 배치가
 * 한 클래스를 공유하던 결합을 끊은 것이 이 분리의 목적이다.
 *
 * <p>enricher 지원 메서드들은 조회/반영을 값으로 주고받아 외부 호출(원천 API·AI·구글)을 트랜잭션 밖에 둔다.
 * 쓰기는 전부 애그리거트 루트({@link ExhibitionRepository}·{@link ExhibitionPlaceRepository}) 경유다.
 */
@Service
@RequiredArgsConstructor
public class ExhibitionSyncFacade {

	private static final Logger log = LoggerFactory.getLogger(ExhibitionSyncFacade.class);

	/** 전시 애그리거트 루트 — 코어 쓰기와 부속(상세·장르) upsert의 단일 진입점. */
	private final ExhibitionRepository exhibitionRepository;
	/** 전시장 애그리거트 루트 — resolve-or-create와 영업시간 정준행 upsert의 단일 진입점. */
	private final ExhibitionPlaceRepository exhibitionPlaceRepository;
	private final ExhibitionCatalogClient catalogClient;
	private final GooglePlaceResponseJpaRepository googlePlaceResponseRepository;
	private final CultureListResponseJpaRepository cultureListResponseRepository;
	private final CultureDetailResponseJpaRepository cultureDetailResponseRepository;
	private final SyncRunRepository syncRunRepository;
	/** 통합 보강 작업큐 — 상세 실패 재시도·이벤트 구동 영업시간 재검증을 enqueue한다(at-least-once의 진입점). */
	private final EnrichmentJobFacade enrichmentJobFacade;

	/**
	 * 장르 백필 1배치의 조회 단계 — 아직 장르가 없는 CATALOG 전시를 최대 {@code max}건 뽑아 분류 입력을 조립한다.
	 * 제목·카테고리는 코어에서, 설명은 상세 satellite에서, 장소명은 전시장 조인에서 조립한다(코어에 없으므로).
	 */
	@Transactional(readOnly = true)
	public List<GenreTarget> findGenreTargets(int max) {
		List<Exhibition> rows = exhibitionRepository.findCatalogWithoutGenre(max);
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<Long, ExhibitionPlace> placesById = placesByIdFor(rows);
		Map<Long, modi.backend.domain.exhibition.catalog.ExhibitionDetail> detailsByExhibitionId = exhibitionRepository
				.findDetails(rows.stream().map(Exhibition::getId).toList()).stream()
				.collect(Collectors.toMap(modi.backend.domain.exhibition.catalog.ExhibitionDetail::getExhibitionId,
						d -> d, (a, b) -> a));
		return rows.stream().map(e -> {
			ExhibitionPlace place = placesById.get(e.getExhibitionPlaceId());
			modi.backend.domain.exhibition.catalog.ExhibitionDetail detail = detailsByExhibitionId.get(e.getId());
			GenreClassification input = new GenreClassification(e.getTitle(),
					e.getCategory() == null ? null : e.getCategory().name(),
					detail == null ? null : detail.getDescription(),
					place == null ? null : place.getName(), null, null);
			return new GenreTarget(e.getId(), input);
		}).toList();
	}

	/**
	 * 장르 백필 1배치의 반영 단계 — 분류 결과를 정준층({@code exhibition_genre})에 쓴다. 전시는 id로 다시 읽어 존재만 확인한다.
	 *
	 * @return 이번 배치로 장르를 부여한 전시 수(0이면 미분류 없음 → 소진)
	 */
	@Transactional
	public int applyGenres(List<GenreTarget> targets, List<GenreResult> results, LocalDateTime now) {
		if (targets.isEmpty()) {
			return 0;
		}
		List<Long> ids = targets.stream().map(GenreTarget::exhibitionId).toList();
		Map<Long, Exhibition> exhibitionsById = exhibitionRepository.findAllActiveByIds(ids).stream()
				.collect(Collectors.toMap(Exhibition::getId, e -> e));
		int applied = 0;
		for (int i = 0; i < targets.size(); i++) {
			GenreResult result = i < results.size() ? results.get(i) : null;
			Exhibition exhibition = exhibitionsById.get(targets.get(i).exhibitionId());
			if (result == null || exhibition == null) {
				continue;
			}
			exhibitionRepository.applyGenre(exhibition.getId(), result, now);
			applied++;
		}
		return applied;
	}

	/** 미분류 CATALOG 전시의 원천 식별자들(GENRE_CLASSIFY 스윕용). 대상이 "미분류 행"이라 멱등하다. */
	@Transactional(readOnly = true)
	public List<String> findUnclassifiedCatalogExternalIds(int limit) {
		return exhibitionRepository.findCatalogWithoutGenre(limit).stream()
				.map(Exhibition::getExternalId)
				.filter(id -> id != null && !id.isBlank())
				.toList();
	}

	/**
	 * GENRE 작업 배치의 <b>조회 단계</b> — 아직 미분류인(정준층 행 없음) 전시만 골라 {@code external_id → 분류 입력}으로 돌려준다.
	 * 이미 분류됐거나 사라진 전시의 external_id는 결과에서 빠진다(호출부가 그 작업을 성공 처리로 마감한다).
	 */
	@Transactional(readOnly = true)
	public Map<String, GenreClassification> resolveGenreInputs(java.util.Collection<String> externalIds) {
		List<Exhibition> exhibitions = exhibitionRepository.findAllByExternalIds(externalIds);
		if (exhibitions.isEmpty()) {
			return Map.of();
		}
		List<Long> ids = exhibitions.stream().map(Exhibition::getId).toList();
		Set<Long> classified = exhibitionRepository.findGenres(ids).stream()
				.map(ExhibitionGenre::getExhibitionId).collect(Collectors.toSet());
		Map<String, GenreClassification> inputs = new java.util.HashMap<>();
		for (Exhibition e : exhibitions) {
			if (!classified.contains(e.getId())) {
				inputs.put(e.getExternalId(), GenreClassification.from(e));
			}
		}
		return inputs;
	}

	/** GENRE 작업 배치의 <b>반영 단계</b> — 분류 성공분만 정준층에 쓴다(AI 폴백분은 호출부가 아예 넘기지 않는다). */
	@Transactional
	public void applyGenreResults(Map<String, GenreResult> resultsByExternalId, LocalDateTime now) {
		if (resultsByExternalId.isEmpty()) {
			return;
		}
		List<Exhibition> exhibitions = exhibitionRepository.findAllByExternalIds(resultsByExternalId.keySet());
		if (exhibitions.isEmpty()) {
			return;
		}
		for (Exhibition e : exhibitions) {
			GenreResult result = resultsByExternalId.get(e.getExternalId());
			if (result != null) {
				exhibitionRepository.applyGenre(e.getId(), result, now);
			}
		}
	}

	/** DETAIL 작업이 만난 대상 전시의 상태(없음/이미완성/상세필요)를 판정한다 — 상세 satellite 행 존재로 판정. */
	@Transactional(readOnly = true)
	public DetailTargetState findDetailTargetState(String externalId) {
		Exhibition exhibition = exhibitionRepository.findByExternalId(externalId).orElse(null);
		if (exhibition == null) {
			return DetailTargetState.MISSING;
		}
		return exhibitionRepository.hasDetail(exhibition.getId())
				? DetailTargetState.ALREADY_SYNCED : DetailTargetState.NEEDS_DETAIL;
	}

	/** DETAIL 작업 반영 — 상세 satellite 채움 + 전시장 보강 + 원본 벤더층 보관. 그 전시장 자연키로 영업시간 재검증을 건다. */
	@Transactional
	public void applyDetailForJob(String externalId, CatalogDetailData detail) {
		Exhibition exhibition = exhibitionRepository.findByExternalId(externalId).orElse(null);
		if (exhibition == null || exhibitionRepository.hasDetail(exhibition.getId())) {
			return;
		}
		applyCatalogDetail(exhibition, detail, LocalDateTime.now());
		archiveDetailOutcome(externalId, detail);
		exhibitionPlaceRepository.findById(exhibition.getExhibitionPlaceId())
				.ifPresent(place -> enqueueHoursRefreshBestEffort(place.getPlaceKey()));
	}

	/** DETAIL 작업 반영 — 원천에 상세가 없으면(빈 응답) 확인 완료행만 남겨 재조회를 막는다(기존 동작). */
	@Transactional
	public void markDetailCheckedForJob(String externalId) {
		Exhibition exhibition = exhibitionRepository.findByExternalId(externalId).orElse(null);
		if (exhibition == null || exhibitionRepository.hasDetail(exhibition.getId())) {
			return;
		}
		exhibitionRepository.markDetailChecked(exhibition.getId(), LocalDateTime.now());
	}

	/**
	 * HOURS_REFRESH 작업의 target_key(= {@code exhibition_place.place_key}, 정규화 이름 — ADR-07)로 조회 대상을 만든다.
	 * 전시장이 없거나 주소가 없으면(질의 불가) 비어 있다 — 호출부가 그 작업을 성공 처리로 마감한다.
	 */
	@Transactional(readOnly = true)
	public java.util.Optional<PlaceHoursTarget> resolvePlaceHoursTarget(String placeKey) {
		return exhibitionPlaceRepository.findByPlaceKey(placeKey)
				.filter(place -> place.getAddress() != null && !place.getAddress().isBlank())
				.map(place -> new PlaceHoursTarget(place.getId(), place.getName(), place.getAddress()));
	}

	/**
	 * 영업시간 조회 대상을 <b>전시장 단위</b>로 최대 {@code maxVenues}개 반환한다 — 자연키가 이름(ADR-07)이라 장소당 1행이
	 * 곧 유료 호출 1회다. 주소가 있고, 아직 정준행이 없거나 {@code staleBefore} 이전 동기화된 전시장을 앞에서부터 채택한다.
	 */
	@Transactional(readOnly = true)
	public List<PlaceHoursTarget> findPlacesNeedingHours(LocalDateTime staleBefore, int maxVenues) {
		return exhibitionPlaceRepository.findPlacesNeedingHours(staleBefore, Math.max(1, maxVenues)).stream()
				.map(p -> new PlaceHoursTarget(p.getId(), p.getName(), p.getAddress()))
				.toList();
	}

	/**
	 * 한 전시장의 조회 결과를 반영한다(전시장 단위 트랜잭션): 벤더 원본 적재 + 정준층(place_hours) 저장.
	 * 읽기가 정준층으로 옮겨졌고 exhibitions의 영업시간 복제 컬럼은 제거됐으므로, 여기서 전시 행은 건드리지 않는다.
	 * {@code data}가 null(미발견)이면 {@code formatted=null}로 값은 비우되 동기화 시각은 남긴다(재조회 백오프).
	 */
	@Transactional
	public void applyVenueHours(PlaceHoursTarget target, PlaceHoursData data, String formatted,
			PlaceHoursVendor vendor, LocalDateTime now) {
		Long placeId = target.exhibitionPlaceId();
		archiveGooglePlaceResponse(placeId, data, vendor, now);
		exhibitionPlaceRepository.applyHours(placeId, formatted, PlaceHoursStatus.of(data, formatted), vendor, now);
	}

	/** 조회가 전송 오류로 실패했음을 정준층에 남긴다(재시도 대상). 표시값·동기화 시각은 지키지 않아 다음 주기 재시도가 유지된다. */
	@Transactional
	public void recordVenueHoursFailure(PlaceHoursTarget target, PlaceHoursVendor vendor) {
		Long placeId = target.exhibitionPlaceId();
		try {
			exhibitionPlaceRepository.markHoursFailure(placeId, vendor);
		} catch (RuntimeException e) {
			log.warn("영업시간 실패 기록 실패(placeId={}, 보강은 계속): {}", placeId, e.getMessage());
		}
	}

	/** 벤더 원본 upsert — 구글이 준 응답만 적재한다(mock은 정준층에 provider=MOCK으로만 남고 벤더층은 비어 있는 게 정상). */
	private void archiveGooglePlaceResponse(Long placeId, PlaceHoursData data, PlaceHoursVendor vendor,
			LocalDateTime now) {
		if (placeId == null || data == null || data.rawJson() == null || vendor != PlaceHoursVendor.GOOGLE) {
			return;
		}
		googlePlaceResponseRepository.findByExhibitionPlaceId(placeId)
				.ifPresentOrElse(row -> {
					row.refresh(data.rawJson(), now);
					googlePlaceResponseRepository.save(row);
				}, () -> googlePlaceResponseRepository.save(
						GooglePlaceResponse.first(placeId, data.rawJson(), now)));
	}

	/**
	 * 외부 전시 API 수집 → DB 적재/완성(목록+상세 한 패스). 신규는 전시장을 resolve-or-create해 상세까지 채워 적재하고,
	 * 기존 미완성은 상세만 채운다. 루프는 트랜잭션 밖, 행 단위 save만 각자 트랜잭션으로 커밋한다.
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
			archiveListResponse(data, syncedAt);
			if (!hasValidPeriod(data)) {
				skipped++;
				continue;
			}
			try {
				switch (syncListedWithDetail(data)) {
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
		archiveSyncRun(run, inserted, completed, skipped, deferred);
		return inserted;
	}

	private void archiveSyncRun(SyncRun run, int inserted, int completed, int skipped, int deferred) {
		try {
			run.finished(inserted, completed, skipped, deferred, LocalDateTime.now());
			syncRunRepository.save(run);
		} catch (RuntimeException e) {
			log.warn("동기화 실행 기록 실패(동기화는 계속): {}", e.getMessage());
		}
	}

	private enum SyncOutcome {
		INSERTED, COMPLETED, SKIPPED
	}

	/** 목록 1건을 상세까지 채워 적재/완성한다. 신규는 전시장 resolve 후 새로 적재, 기존 미완성 행은 상세만 채운다. */
	private SyncOutcome syncListedWithDetail(CatalogExhibitionData data) {
		Exhibition existing = exhibitionRepository.findByExternalId(data.externalId()).orElse(null);
		if (existing != null) {
			if (exhibitionRepository.hasDetail(existing.getId())) {
				return SyncOutcome.SKIPPED;
			}
			applyDetailOrCheck(existing);
			return SyncOutcome.COMPLETED;
		}
		// 신규는 상세를 <b>먼저</b> 받아본다 — 상세 호출이 일시 실패하면 아무것도 적재하지 않고 이 행만 다음 주기로 연기한다
		// (불완전한 행·전시장만 남기지 않는다). 상세가 성공/빈 응답일 때만 전시장 resolve + 전시·상세 적재로 진행한다.
		java.util.Optional<CatalogDetailData> detail = fetchDetailDeferring(data.externalId());
		ExhibitionPlace place = exhibitionPlaceRepository.resolveOrCreate(data.place(), data.region(), data.sigungu(),
				data.gpsX(), data.gpsY());
		Exhibition saved = exhibitionRepository.save(Exhibition.createCatalog(data.externalId(), data.title(),
				place.getId(), data.startDate(), data.endDate(), data.category(), data.posterUrl(), data.detailUrl(),
				data.serviceName()));
		LocalDateTime now = LocalDateTime.now();
		detail.ifPresentOrElse(d -> applyCatalogDetail(saved, d, now),
				() -> exhibitionRepository.markDetailChecked(saved.getId(), now));
		archiveDetailOutcome(data.externalId(), detail.orElse(null));
		// 이벤트 구동 재검증(설계 §4-1): 새 전시가 기존 장소(place_hours 존재)에 들어오면 재검증 enqueue한다. target_key는
		// 전시장 자연키(정규화 이름). 가드(기존 장소만·최소 간격·UK 중복)는 큐 파사드가 판단한다.
		enqueueHoursRefreshBestEffort(place.getPlaceKey());
		return SyncOutcome.INSERTED;
	}

	/** 영업시간 재검증 enqueue는 부가 작업이라 어떤 실패(큐 저장 오류 등)도 동기화를 깨지 않는다 — 이 장소만 건너뛴다. */
	private void enqueueHoursRefreshBestEffort(String placeKey) {
		if (placeKey == null) {
			return;
		}
		try {
			enrichmentJobFacade.enqueueHoursRefresh(placeKey, LocalDateTime.now());
		} catch (RuntimeException e) {
			log.warn("영업시간 재검증 enqueue 실패(placeKey={}, 동기화는 계속): {}", placeKey, e.getMessage());
		}
	}

	/** 원천 상세2를 받아 상세를 채우거나(있음), 상세 미보유(빈 응답)면 확인 완료행만 남긴다. 일시 실패는 예외로 전파된다(기존 행 완성 경로). */
	private void applyDetailOrCheck(Exhibition exhibition) {
		java.util.Optional<CatalogDetailData> detail = fetchDetailDeferring(exhibition.getExternalId());
		LocalDateTime now = LocalDateTime.now();
		detail.ifPresentOrElse(d -> applyCatalogDetail(exhibition, d, now),
				() -> exhibitionRepository.markDetailChecked(exhibition.getId(), now));
		archiveDetailOutcome(exhibition.getExternalId(), detail.orElse(null));
	}

	/** 상세를 조회한다. 일시 실패면 재시도 작업(DETAIL_SYNC)을 남기고 예외를 전파해 호출부가 이 행만 연기하게 한다. */
	private java.util.Optional<CatalogDetailData> fetchDetailDeferring(String externalId) {
		try {
			return catalogClient.fetchDetail(externalId);
		} catch (RuntimeException e) {
			// 진행 상태(재시도)는 통합 작업큐가 안다 — 상세 실패는 DETAIL_SYNC 작업으로 남겨 백오프 재시도되게 한다
			// (V29에서 culture_detail_response 상태머신은 제거됐다). 예외는 전파해 이 행만 연기하는 동작은 불변.
			enqueueDetailRetryBestEffort(externalId);
			throw e;
		}
	}

	/** 상세 값을 상세 satellite에 upsert(전시 애그리거트)하고, 전시장 보강 필드(주소/전화/홈페이지)를 채운다 — 두 애그리거트 조율. */
	private void applyCatalogDetail(Exhibition exhibition, CatalogDetailData d, LocalDateTime now) {
		exhibitionRepository.applyDetail(exhibition.getId(), d.price(), d.description(), d.imgUrl(), now);
		exhibitionPlaceRepository.findById(exhibition.getExhibitionPlaceId()).ifPresent(place -> {
			place.enrichDetail(d.placeAddr(), d.phone(), d.placeUrl());
			exhibitionPlaceRepository.save(place);
		});
	}

	private void archiveListResponse(CatalogExhibitionData data, LocalDateTime syncedAt) {
		if (data.payload() == null) {
			return;
		}
		try {
			cultureListResponseRepository.findByExternalId(data.externalId())
					.ifPresentOrElse(row -> {
						row.seenAgain(data.payload(), syncedAt);
						cultureListResponseRepository.save(row);
					}, () -> cultureListResponseRepository.save(
							CultureListResponse.first(data.externalId(), data.payload(), syncedAt)));
		} catch (RuntimeException e) {
			log.warn("목록 원본 적재 실패(externalId={}, 동기화는 계속): {}", data.externalId(), e.getMessage());
		}
	}

	/**
	 * 상세 원본을 벤더층에 upsert한다(순수 원본 보관소 — 설계 §2). {@code data}가 있을 때만 기록한다:
	 * 원천에 상세가 없으면(빈 응답) 남길 원본이 없어 행을 만들지 않는다(그 사실은 상세 satellite 행 존재가 안다).
	 */
	private void archiveDetailOutcome(String externalId, CatalogDetailData data) {
		if (data == null) {
			return;
		}
		try {
			cultureDetailResponseRepository.findByExternalId(externalId)
					.ifPresentOrElse(row -> {
						row.refresh(data.payload());
						cultureDetailResponseRepository.save(row);
					}, () -> cultureDetailResponseRepository.save(
							CultureDetailResponse.first(externalId, data.payload())));
		} catch (RuntimeException e) {
			log.warn("상세 원본 적재 실패(externalId={}, 동기화는 계속): {}", externalId, e.getMessage());
		}
	}

	/**
	 * 상세 조회 실패를 재시도 작업으로 남긴다(DETAIL_SYNC). 진행 상태·재시도는 통합 작업큐가 맡으므로 벤더 테이블엔
	 * 아무것도 쓰지 않는다. 큐 저장 실패는 동기화를 깨지 않는다 — 예외는 상위(applyDetailOrCheck)가 전파해 이 행만 연기한다.
	 */
	private void enqueueDetailRetryBestEffort(String externalId) {
		try {
			enrichmentJobFacade.enqueue(JobType.DETAIL_SYNC, externalId, LocalDateTime.now());
		} catch (RuntimeException e) {
			log.warn("상세 재시도 작업 enqueue 실패(externalId={}, 동기화는 계속): {}", externalId, e.getMessage());
		}
	}

	private static boolean hasValidPeriod(CatalogExhibitionData data) {
		return data.startDate() == null || data.endDate() == null || !data.startDate().isAfter(data.endDate());
	}

	private Map<Long, ExhibitionPlace> placesByIdFor(List<Exhibition> exhibitions) {
		Set<Long> placeIds = exhibitions.stream().map(Exhibition::getExhibitionPlaceId).collect(Collectors.toSet());
		return exhibitionPlaceRepository.findAllByIds(placeIds).stream()
				.collect(Collectors.toMap(ExhibitionPlace::getId, p -> p, (a, b) -> a));
	}
}
