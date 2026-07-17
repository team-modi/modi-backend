package modi.backend.application.exhibition;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.bookmark.ExhibitionBookmarkRepository;
import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.CatalogExhibitionData;
import modi.backend.domain.exhibition.CatalogListData;
import modi.backend.domain.exhibition.CultureDetailResponse;
import modi.backend.domain.exhibition.CultureDetailResponseRepository;
import modi.backend.domain.exhibition.CultureListResponse;
import modi.backend.domain.exhibition.CultureListResponseRepository;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.domain.exhibition.ExhibitionFormat;
import modi.backend.domain.exhibition.ExhibitionGenre;
import modi.backend.domain.exhibition.ExhibitionGenreRepository;
import modi.backend.domain.exhibition.ExhibitionQuery;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.domain.exhibition.ExhibitionRegionGroup;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.domain.exhibition.ExhibitionSection;
import modi.backend.domain.exhibition.GenreClassification;
import modi.backend.domain.exhibition.GenreClassifier;
import modi.backend.domain.exhibition.GenreKeyword;
import modi.backend.domain.exhibition.GenreResult;
import modi.backend.domain.exhibition.GooglePlaceResponse;
import modi.backend.domain.exhibition.GooglePlaceResponseRepository;
import modi.backend.domain.exhibition.JobType;
import modi.backend.domain.exhibition.PlaceHours;
import modi.backend.domain.exhibition.PlaceHoursData;
import modi.backend.domain.exhibition.PlaceHoursRepository;
import modi.backend.domain.exhibition.PlaceHoursStatus;
import modi.backend.domain.exhibition.PlaceHoursVendor;
import modi.backend.domain.exhibition.PlaceKey;
import modi.backend.domain.exhibition.SyncRun;
import modi.backend.domain.exhibition.SyncRunRepository;
import modi.backend.domain.exhibition.SyncTrigger;
import modi.backend.domain.venue.Venue;
import modi.backend.domain.venue.VenueErrorCode;
import modi.backend.domain.venue.VenueRepository;
import modi.backend.infra.record.RecordJpaRepository;
import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;
import modi.backend.support.response.Cursor;
import modi.backend.support.time.AppTime;

/**
 * 전시 유스케이스 조율(03_전시.md). load·조율·save만 하고, 상태 변경·규칙 판단은 {@link Exhibition} 메서드에 위임한다.
 * 목록은 커서(키셋) 페이지네이션 — latest/ending/popular은 DB 키셋, distance는 앱 레이어 정렬(best-effort).
 * CATALOG는 외부 API로 동기화해 DB에 upsert하고 조회/등록은 DB만 본다(수집-적재 방식).
 */
@Service
@RequiredArgsConstructor
public class ExhibitionFacade {

	private static final Logger log = LoggerFactory.getLogger(ExhibitionFacade.class);
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 50;
	private static final int ENDING_SOON_DAYS = 7;
	private static final int BANNER_LIMIT = 3;
	// 영업시간 보강 1회 실행에서 스캔하는 전시 상한(장소 그룹화 대상). 실제 외부 호출은 장소 수(maxVenuesPerRun)로 별도 제한된다.
	private static final int PLACE_HOURS_SCAN_LIMIT = 3000;

	private final ExhibitionRepository exhibitionRepository;
	private final ExhibitionCatalogClient catalogClient;
	private final ExhibitionBookmarkRepository exhibitionBookmarkRepository;
	private final VenueRepository venueRepository;
	// 크로스 도메인 실용 조회: 상세의 recorded 계산을 위해 record의 Spring Data 리포지토리를 직접 읽는다(전용 포트 미도입).
	private final RecordJpaRepository recordJpaRepository;
	/** 영업시간 정준층 — 장소당 1행(place_key). 상세의 operatingHours가 여기서 나간다. */
	private final PlaceHoursRepository placeHoursRepository;
	/** 구글 응답 원본 — 벤더층. 변환 규칙이 바뀌면 여기서 정준을 재생성한다(재호출·과금 없이). */
	private final GooglePlaceResponseRepository googlePlaceResponseRepository;
	/** 장르 분류 전략(랜덤/AI) — 주입되는 구현은 {@code app.exhibition.genre.classifier}로 선택된다(@Primary). */
	private final GenreClassifier genreClassifier;
	/** 장르 정준층 — 분류 결과를 계보(provider·model)와 함께 남긴다. 전시와는 ID로만 참조한다. */
	private final ExhibitionGenreRepository exhibitionGenreRepository;
	/** 목록(realm2) 응답 원본 — 벤더층. 재파싱 원료·원천 정정 감지용(도메인 적재와 병행 기록). */
	private final CultureListResponseRepository cultureListResponseRepository;
	/** 상세(detail2) 응답 원본 + 수집 상태기계 — 벤더층. */
	private final CultureDetailResponseRepository cultureDetailResponseRepository;
	/** 동기화 실행 기록 — 원천을 다 가져왔나(조용한 절단 감지)와 실행 집계를 남긴다. */
	private final SyncRunRepository syncRunRepository;
	/** 통합 보강 작업큐 — 상세 실패 재시도·이벤트 구동 영업시간 재검증을 enqueue한다(at-least-once의 진입점). */
	private final EnrichmentJobFacade enrichmentJobFacade;

	/** 지역 필터 그룹 목록(디자인 병합 칩). 정적 enum 메타데이터라 조회 없이 변환만 한다. */
	public List<ExhibitionResult.RegionGroup> getRegionGroups() {
		return ExhibitionRegionGroup.all().stream().map(ExhibitionResult.RegionGroup::from).toList();
	}

	/**
	 * 목록/탐색(5.2). 필터 미지정 시 오늘 진행 중인 전시를 기본 노출한다. 비로그인은 CATALOG만.
	 * 커서의 정렬 판별자는 현재 sort와 일치해야 한다(Cursor.decode가 검증 → INVALID_CURSOR).
	 */
	@Transactional(readOnly = true)
	public ExhibitionResult.ListPage search(ExhibitionCriteria.Search criteria) {
		LocalDate today = LocalDate.now(AppTime.KST);
		String keyword = normalizeKeyword(criteria.keyword());
		List<ExhibitionRegion> regions = parseRegions(criteria.region());
		List<ExhibitionCategory> categories = parseCategories(criteria.category());
		ExhibitionSection section = ExhibitionSection.from(criteria.section());
		String sort = canonicalSort(criteria.sort());
		int size = clampSize(criteria.size());
		LocalDate ongoingOn = resolveOngoingOn(criteria.date(), keyword, regions, categories, section, today);

		if ("distance".equals(sort)) {
			return searchByDistance(criteria, today, size,
					buildQuery(keyword, ongoingOn, regions, categories, section, today, criteria.period(),
							"distance", null, null, criteria.requesterId()));
		}

		Cursor cursor = Cursor.decode(criteria.cursor(), sort).orElse(null);
		String cursorKey = cursor == null ? null : cursor.key();
		Long cursorId = cursor == null ? null : cursor.lastId();
		ExhibitionQuery query = buildQuery(keyword, ongoingOn, regions, categories, section, today,
				criteria.period(), sort, cursorKey, cursorId, criteria.requesterId());

		List<Exhibition> rows = exhibitionRepository.searchSlice(query, size + 1);
		boolean hasNext = rows.size() > size;
		List<Exhibition> page = hasNext ? rows.subList(0, size) : rows;

		Set<Long> bookmarked = bookmarkedIds(criteria.requesterId(), page);
		List<ExhibitionResult.ListItem> content = page.stream()
				.map(e -> ExhibitionResult.ListItem.from(e, today, bookmarked.contains(e.getId())))
				.toList();
		String nextCursor = hasNext ? encodeCursor(sort, page.get(page.size() - 1)) : null;
		long totalCount = exhibitionRepository.count(query);
		return new ExhibitionResult.ListPage(content, nextCursor, hasNext, totalCount);
	}

	/**
	 * 홈 배너(03_전시.md E-10). 운영자 지정 기능은 아직 없어, 오늘 진행 중인 전시 중 조회수 상위 최대 3개를 노출한다.
	 * 진행 중 전시가 없으면 빈 배열을 반환한다(홈은 배너 부재 시 섹션만 노출).
	 */
	@Transactional(readOnly = true)
	public List<ExhibitionResult.Banner> banners() {
		return exhibitionRepository.findOngoingCatalogTopByViews(LocalDate.now(AppTime.KST), BANNER_LIMIT)
				.stream().map(ExhibitionResult.Banner::from).toList();
	}

	/**
	 * 거리순(5.2, P2 best-effort). lat·lng 필수. 후보를 앱 레이어에서 단순 제곱거리로 정렬(좌표 null은 뒤로)하고,
	 * 커서는 마지막 id 위치 기준으로 슬라이스한다(완전한 키셋 대신 후보 재조회 — 단순화).
	 */
	private ExhibitionResult.ListPage searchByDistance(ExhibitionCriteria.Search criteria, LocalDate today,
			int size, ExhibitionQuery query) {
		if (criteria.lat() == null || criteria.lng() == null) {
			throw new CoreException(ErrorType.INVALID_INPUT, "거리순 정렬은 lat·lng가 필요합니다.");
		}
		double lat = criteria.lat();
		double lng = criteria.lng();
		List<Exhibition> ordered = exhibitionRepository.searchAll(query).stream()
				.sorted(distanceComparator(lat, lng))
				.toList();

		Cursor cursor = Cursor.decode(criteria.cursor(), "distance").orElse(null);
		int start = cursor == null ? 0 : nextIndexAfter(ordered, cursor.lastId());
		int end = Math.min(start + size, ordered.size());
		List<Exhibition> page = start >= ordered.size() ? List.of() : ordered.subList(start, end);
		boolean hasNext = end < ordered.size();

		Set<Long> bookmarked = bookmarkedIds(criteria.requesterId(), page);
		List<ExhibitionResult.ListItem> content = page.stream()
				.map(e -> ExhibitionResult.ListItem.from(e, today, bookmarked.contains(e.getId())))
				.toList();
		String nextCursor = null;
		if (hasNext) {
			Exhibition last = page.get(page.size() - 1);
			nextCursor = Cursor.of("distance", String.valueOf(distanceSq(last, lat, lng)), last.getId()).encode();
		}
		return new ExhibitionResult.ListPage(content, nextCursor, hasNext, ordered.size());
	}

	/**
	 * 상세(5.3). 없으면 404, 타인의 CUSTOM이면 403. CATALOG 최초 진입 시 상세를 1회 지연 수집해 캐시한다.
	 * bookmarked·recorded는 요청자 기준으로 채운다(비로그인 false).
	 */
	@Transactional
	public ExhibitionResult.Detail getDetail(ExhibitionCriteria.Detail criteria) {
		Exhibition exhibition = exhibitionRepository.findById(criteria.exhibitionId())
				.orElseThrow(() -> new CoreException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND));
		if (!exhibition.isAccessibleBy(criteria.requesterId())) {
			throw new CoreException(ErrorType.FORBIDDEN, "타인의 개인 전시 접근: " + criteria.exhibitionId());
		}
		if (exhibition.isCatalog() && !exhibition.isDetailSynced()) {
			try {
				catalogClient.fetchDetail(exhibition.getExternalId()).ifPresent(exhibition::applyDetail);
			} catch (CoreException ex) {
				// 외부 실패 시 base 필드만 반환 — detailSyncedAt이 null로 남아 다음 조회에서 재시도된다.
			}
		}
		exhibition.increaseView();
		exhibitionRepository.save(exhibition);
		Long requesterId = criteria.requesterId();
		boolean bookmarked = requesterId != null
				&& exhibitionBookmarkRepository.existsActive(requesterId, exhibition.getId());
		boolean recorded = requesterId != null
				&& recordJpaRepository.existsByUserIdAndExhibitionIdAndDeletedAtIsNull(requesterId, exhibition.getId());
		return ExhibitionResult.Detail.from(exhibition, genreOf(exhibition.getId()),
				placeHoursOf(exhibition.getPlaceKey()), bookmarked, recorded);
	}

	/** 스냅샷/조회용 — 조회수 증가·외부 상세수집·개인화 없이 DB에서만 전시를 읽어 반환한다(기록 생성 등 내부 사용). */
	@Transactional(readOnly = true)
	public ExhibitionResult.Detail getForSnapshot(Long exhibitionId, Long requesterId) {
		Exhibition e = exhibitionRepository.findById(exhibitionId)
				.orElseThrow(() -> new CoreException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND));
		if (!e.isAccessibleBy(requesterId)) {
			throw new CoreException(ErrorType.FORBIDDEN, "타인의 개인 전시 접근: " + exhibitionId);
		}
		return ExhibitionResult.Detail.from(e, genreOf(e.getId()), placeHoursOf(e.getPlaceKey()), false, false);
	}

	/**
	 * 상세의 keywords 출처 — 정준층({@code exhibition_genre})에서 읽는다(이관 2단계-b, 읽기 전환).
	 * 미분류면 null이고 상세는 빈 배열을 내려보낸다. {@code exhibitions.genre_keyword}로 폴백하지 않는다 —
	 * 폴백은 진실 원천을 모호하게 만들고, 정준층 쓰기가 빠져도 아무도 눈치채지 못하게 한다.
	 */
	private ExhibitionGenre genreOf(Long exhibitionId) {
		return exhibitionGenreRepository.findByExhibitionId(exhibitionId).orElse(null);
	}

	/**
	 * 개인 전시 등록(5.4). 제목 필수·기간·개인전 작가 검증은 Entity에서 수행한다.
	 * venueId가 있으면 전시관에서 장소·지역(요청 region 미지정 시)을 파생한다(없는 venueId면 404). 장르 키워드는 마스터에서 무작위로 1개 부여(임시).
	 */
	@Transactional
	public ExhibitionResult.Created registerCustom(ExhibitionCriteria.CustomCreate criteria) {
		ExhibitionRegion region = criteria.region() == null ? null : ExhibitionRegion.from(criteria.region());
		ExhibitionCategory category = criteria.category() == null ? null
				: ExhibitionCategory.from(criteria.category());
		ExhibitionFormat format = criteria.format() == null ? null : ExhibitionFormat.from(criteria.format());
		String place = criteria.place();
		if (criteria.venueId() != null) {
			Venue venue = venueRepository.findById(criteria.venueId())
					.orElseThrow(() -> new CoreException(VenueErrorCode.VENUE_NOT_FOUND));
			place = venue.getName();
			if (region == null) {
				region = venue.getRegion();
			}
		}
		// 장르: 사용자가 장르 시트에서 직접 고르면 그 값(마스터 검증 + provider=USER), 미지정이면 분류기(랜덤/AI)가 부여한다.
		// 분류기는 실패해도 예외를 던지지 않고 유효 장르를 반환하므로 등록 흐름을 깨지 않는다(폴백 시 provider=RANDOM).
		GenreResult genre;
		if (criteria.genreKeyword() != null && !criteria.genreKeyword().isBlank()) {
			if (!GenreKeyword.contains(criteria.genreKeyword())) {
				throw new CoreException(ErrorType.INVALID_INPUT, "정의되지 않은 장르 키워드: " + criteria.genreKeyword());
			}
			genre = GenreResult.user(criteria.genreKeyword());
		} else {
			genre = genreClassifier.classify(new GenreClassification(criteria.title(),
					category == null ? null : category.name(), null, place, criteria.artist(), null));
		}
		Exhibition exhibition = Exhibition.createCustom(criteria.ownerId(), criteria.title(), place,
				criteria.startDate(), criteria.endDate(), region, category, format, criteria.artist(),
				criteria.posterUrl());
		Exhibition saved = exhibitionRepository.save(exhibition);
		// 장르는 정준층에만 남긴다(레거시 컬럼은 7단계에서 제거). 신규 전시라 항상 생성(upsert 분기 불필요).
		exhibitionGenreRepository.save(ExhibitionGenre.create(saved.getId(), genre.genreKeyword(),
				genre.provider(), genre.model(), LocalDateTime.now()));
		return ExhibitionResult.Created.from(saved);
	}

	/**
	 * 개인 전시(CUSTOM) 동반 삭제 — 기록 삭제 시, 그 기록이 직접 만든 전시를 더는 어떤 기록도 참조하지 않을 때 호출된다.
	 * 본인이 등록한 CUSTOM만 soft-delete하고(공용 CATALOG·타인 전시·이미 삭제된 전시는 무시) 멱등하게 동작한다.
	 * (기록을 지워도 전시가 '내 전시' 목록에 남아 조회되던 고아 전시 문제 방지)
	 */
	@Transactional
	public void deleteCustomOwnedBy(Long exhibitionId, Long ownerId) {
		exhibitionRepository.findById(exhibitionId)
				.filter(exhibition -> exhibition.isCustomOwnedBy(ownerId))
				.ifPresent(exhibition -> {
					exhibition.delete();
					exhibitionRepository.save(exhibition);
				});
	}

	/**
	 * 장르 백필 1배치의 <b>조회 단계</b> — 아직 장르가 없는 CATALOG(공공데이터) 전시를 최대 {@code max}건 값으로 뽑아 나온다.
	 * AI 호출은 이 트랜잭션 밖({@link CatalogEnricher})에서 일어나고, 반영은 {@link #applyGenres}가 별도 트랜잭션으로 맡는다
	 * — 최대 60초(+429 재시도 백오프)가 걸리는 외부 호출에 DB 커넥션을 물리지 않기 위함이다({@link PlaceHoursEnricher}와 동형).
	 * 대상이 "미분류 행"이라 멱등하다 — 반복 실행돼도 신규 행만 나온다.
	 */
	@Transactional(readOnly = true)
	public List<GenreTarget> findGenreTargets(int max) {
		return exhibitionRepository.findCatalogWithoutGenre(max).stream()
				.map(e -> new GenreTarget(e.getId(), GenreClassification.from(e)))
				.toList();
	}

	/**
	 * 장르 백필 1배치의 <b>반영 단계</b> — 분류 결과를 정준층({@code exhibition_genre})에 쓴다(레거시 컬럼은 7단계에서 제거).
	 * <p>
	 * 정준층에 {@code provider}를 남기는 이유: 출처를 안 남기면 랜덤 폴백값이 AI 분류값과 구분되지 않아, 저장되는 순간
	 * 미분류(IS NULL) 대상에서 빠져 <b>영구 이탈</b>한다. {@code provider=RANDOM}이 남으면 선별 재분류가 가능해진다.
	 * <p>
	 * 전시는 id로 다시 읽어 <b>존재만 확인</b>한다 — 조회~반영 사이에 사라진 전시는 건너뛴다(정준층에도 남기지 않는다).
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
		Map<Long, ExhibitionGenre> genresById = exhibitionGenreRepository.findAllByExhibitionIds(ids).stream()
				.collect(Collectors.toMap(ExhibitionGenre::getExhibitionId, g -> g));
		int applied = 0;
		for (int i = 0; i < targets.size(); i++) {
			GenreResult result = i < results.size() ? results.get(i) : null;
			Exhibition exhibition = exhibitionsById.get(targets.get(i).exhibitionId());
			if (result == null || exhibition == null) {
				continue;
			}
			// 전시 존재만 확인하고(위 exhibitionsById), 장르는 정준층에만 쓴다(레거시 컬럼 제거 — 7단계).
			saveCanonicalGenre(exhibition.getId(), result, genresById.get(exhibition.getId()), now);
			applied++;
		}
		return applied;
	}

	/** 정준층 upsert — 없으면 생성, 있으면 재분류(분류는 덮어쓰기가 정상 동작이다: 모델 업그레이드·폴백 복구). */
	private void saveCanonicalGenre(Long exhibitionId, GenreResult result, ExhibitionGenre existing,
			LocalDateTime now) {
		if (existing == null) {
			exhibitionGenreRepository.save(ExhibitionGenre.create(exhibitionId, result.genreKeyword(),
					result.provider(), result.model(), now));
			return;
		}
		existing.reclassify(result.genreKeyword(), result.provider(), result.model(), now);
		exhibitionGenreRepository.save(existing);
	}

	// ── 통합 작업큐 처리기(enricher) 지원 — 조회/반영을 값으로 주고받아 외부 호출을 트랜잭션 밖에 둔다 ──────────

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
		Set<Long> classified = exhibitionGenreRepository.findAllByExhibitionIds(ids).stream()
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
		List<Long> ids = exhibitions.stream().map(Exhibition::getId).toList();
		Map<Long, ExhibitionGenre> existing = exhibitionGenreRepository.findAllByExhibitionIds(ids).stream()
				.collect(Collectors.toMap(ExhibitionGenre::getExhibitionId, g -> g));
		for (Exhibition e : exhibitions) {
			GenreResult result = resultsByExternalId.get(e.getExternalId());
			if (result != null) {
				saveCanonicalGenre(e.getId(), result, existing.get(e.getId()), now);
			}
		}
	}

	/** DETAIL 작업이 만난 대상 전시의 상태(없음/이미완성/상세필요)를 판정한다(외부 호출은 호출부가 트랜잭션 밖에서). */
	@Transactional(readOnly = true)
	public DetailTargetState findDetailTargetState(String externalId) {
		Exhibition exhibition = exhibitionRepository.findByExternalId(externalId).orElse(null);
		if (exhibition == null) {
			return DetailTargetState.MISSING;
		}
		return exhibition.isDetailSynced() ? DetailTargetState.ALREADY_SYNCED : DetailTargetState.NEEDS_DETAIL;
	}

	/** DETAIL 작업 반영 — 받아온 상세를 전시에 채우고 원본을 벤더층에 보관한다. 상세로 확정된 place_key로 재검증도 건다. */
	@Transactional
	public void applyDetailForJob(String externalId, CatalogDetailData detail) {
		Exhibition exhibition = exhibitionRepository.findByExternalId(externalId).orElse(null);
		if (exhibition == null || exhibition.isDetailSynced()) {
			return;
		}
		exhibition.applyDetail(detail);
		exhibitionRepository.save(exhibition);
		archiveDetailOutcome(externalId, detail);
		enqueueHoursRefreshBestEffort(exhibition.getPlaceKey());
	}

	/** DETAIL 작업 반영 — 원천에 상세가 없으면(빈 응답) 확인 완료만 표기해 재조회를 막는다(기존 동작). */
	@Transactional
	public void markDetailCheckedForJob(String externalId) {
		Exhibition exhibition = exhibitionRepository.findByExternalId(externalId).orElse(null);
		if (exhibition == null || exhibition.isDetailSynced()) {
			return;
		}
		exhibition.markDetailChecked();
		exhibitionRepository.save(exhibition);
	}

	/** HOURS_REFRESH 작업의 place_key로 조회 대상(장소명·주소·전시 id들)을 만든다. 그 장소를 쓰는 전시가 없으면 비어 있다. */
	@Transactional(readOnly = true)
	public java.util.Optional<PlaceHoursTarget> resolvePlaceHoursTarget(String placeKey) {
		List<Exhibition> exhibitions = exhibitionRepository.findCatalogByPlaceKey(placeKey);
		if (exhibitions.isEmpty()) {
			return java.util.Optional.empty();
		}
		List<Long> ids = exhibitions.stream().map(Exhibition::getId).toList();
		String placeName = exhibitions.get(0).getPlace();
		String placeAddr = exhibitions.stream().map(Exhibition::getPlaceAddr)
				.filter(a -> a != null && !a.isBlank()).findFirst().orElse(placeKey);
		return java.util.Optional.of(new PlaceHoursTarget(placeName, placeAddr, ids));
	}

	/**
	 * 영업시간 조회 대상을 <b>장소(placeAddr) 단위</b>로 묶어 최대 {@code maxVenues}개 반환한다 — 같은 장소 전시는 1콜로 처리하기 위함.
	 * 대상 전시는 {@code staleBefore} 이전 조회분·미조회분(주소 있는 CATALOG)이며, 스캔 상한 내에서 등장한 장소들을 앞에서부터 채택한다.
	 */
	@Transactional(readOnly = true)
	public List<PlaceHoursTarget> findVenuesNeedingHours(java.time.LocalDateTime staleBefore, int maxVenues) {
		List<Exhibition> candidates = exhibitionRepository.findCatalogNeedingOperatingHours(
				staleBefore, PLACE_HOURS_SCAN_LIMIT);
		// placeAddr → (대표 장소명 + 전시 id들). 등장 순서(placeAddr asc) 보존 + 장소 수 상한.
		java.util.LinkedHashMap<String, java.util.List<Long>> idsByAddr = new java.util.LinkedHashMap<>();
		java.util.Map<String, String> nameByAddr = new java.util.HashMap<>();
		for (Exhibition e : candidates) {
			String addr = e.getPlaceAddr();
			if (addr == null || addr.isBlank()) {
				continue;
			}
			if (!idsByAddr.containsKey(addr) && idsByAddr.size() >= Math.max(1, maxVenues)) {
				continue; // 장소 상한 도달 — 새 장소는 다음 주기에
			}
			idsByAddr.computeIfAbsent(addr, k -> new java.util.ArrayList<>()).add(e.getId());
			nameByAddr.putIfAbsent(addr, e.getPlace());
		}
		return idsByAddr.entrySet().stream()
				.map(en -> new PlaceHoursTarget(nameByAddr.get(en.getKey()), en.getKey(), en.getValue()))
				.toList();
	}

	/**
	 * 한 장소의 조회 결과를 반영한다(장소 단위 트랜잭션): 벤더 원본 적재 + 정준층 저장 + 그 장소 전시들에 표시값 저장.
	 * <p>
	 * {@code exhibitions.operating_hours}에도 <b>계속</b> 쓴다(쓰기 이중화) — 읽기는 정준층으로 옮겼지만 컬럼 정리는
	 * 7단계의 몫이고, 그때까지 이 컬럼이 즉시 복구 가능한 안전망으로 남는다.
	 * {@code data}가 null(미발견)이면 전시엔 {@code formatted=null}로 값은 비우되 조회 시각만 남긴다(재조회 백오프 — 기존 동작).
	 */
	@Transactional
	public void applyVenueHours(PlaceHoursTarget target, PlaceHoursData data, String formatted,
			PlaceHoursVendor vendor, java.time.LocalDateTime now) {
		String placeKey = PlaceKey.of(target.placeAddr());
		archiveGooglePlaceResponse(placeKey, data, vendor, now);
		savePlaceHours(placeKey, formatted, PlaceHoursStatus.of(data, formatted), vendor, now);
		for (Exhibition e : exhibitionRepository.findAllActiveByIds(target.exhibitionIds())) {
			e.applyOperatingHours(formatted, now);
			exhibitionRepository.save(e);
		}
	}

	/**
	 * 조회가 전송 오류로 실패했음을 정준층에 남긴다(재시도 대상). <b>표시값은 지우지 않는다</b> —
	 * 부가 기능의 일시 장애로 사용자에게 보이던 영업시간이 사라지면 그건 서비스 후퇴다.
	 * {@code operating_hours_synced_at}도 남기지 않아 다음 주기 재시도라는 기존 동작이 그대로 유지된다.
	 */
	@Transactional
	public void recordVenueHoursFailure(PlaceHoursTarget target, PlaceHoursVendor vendor) {
		try {
			savePlaceHours(PlaceKey.of(target.placeAddr()), null, PlaceHoursStatus.FAILED, vendor, LocalDateTime.now());
		} catch (RuntimeException e) {
			log.warn("영업시간 실패 기록 실패(장소={}, 보강은 계속): {}", target.placeAddr(), e.getMessage());
		}
	}

	/**
	 * 벤더 원본 upsert. <b>구글이 준 응답만</b> 적재한다 — 이 테이블은 구글 전용 벤더 테이블이라(카카오가 오면
	 * {@code kakao_place_response}가 따로 생긴다) mock이 만든 값을 넣으면 "구글이 이렇게 답했다"는 거짓이 된다.
	 * mock 실행은 정준층에 {@code provider=MOCK}으로만 남고 벤더층은 비어 있는 게 정상이다.
	 */
	private void archiveGooglePlaceResponse(String placeKey, PlaceHoursData data, PlaceHoursVendor vendor,
			java.time.LocalDateTime now) {
		if (placeKey == null || data == null || data.rawJson() == null || vendor != PlaceHoursVendor.GOOGLE) {
			return;
		}
		googlePlaceResponseRepository.findByPlaceKey(placeKey)
				.ifPresentOrElse(row -> {
					row.refresh(data.rawJson(), now);
					googlePlaceResponseRepository.save(row);
				}, () -> googlePlaceResponseRepository.save(
						GooglePlaceResponse.first(placeKey, data.rawJson(), now)));
	}

	/** 정준층 upsert — 없으면 생성, 있으면 갱신(영업시간은 바뀌는 값이라 덮어쓰기가 정상 동작이다). synced_at은 성공 확인 시각만 남긴다(재검증 간격 판정 기준). */
	private void savePlaceHours(String placeKey, String formatted, PlaceHoursStatus status,
			PlaceHoursVendor vendor, LocalDateTime now) {
		if (placeKey == null) {
			return; // 주소 없는 장소는 키가 없다 — 대상 선별이 placeAddr is not null이라 실제로는 오지 않는다.
		}
		placeHoursRepository.findByPlaceKey(placeKey)
				.ifPresentOrElse(row -> {
					row.refresh(formatted, status, vendor, now);
					placeHoursRepository.save(row);
				}, () -> placeHoursRepository.save(PlaceHours.first(placeKey, formatted, status, vendor, now)));
	}

	/**
	 * 상세의 operatingHours 출처 — 정준층({@code place_hours})에서 읽는다(이관 4단계, 읽기 전환).
	 * 조회된 적 없는 장소면 null이고 상세는 값을 안 내려보낸다. {@code exhibitions.operating_hours}로 폴백하지
	 * <b>않는다</b> — 폴백은 두 곳이 갈렸을 때 어느 쪽이 진실인지 모호하게 만들고, 정준층 쓰기가 통째로 빠져도
	 * 아무도 눈치채지 못하게 한다. 전 경로가 덮인다는 근거는 쓰기 이중화와 V23 백필 두 개다.
	 */
	private PlaceHours placeHoursOf(String placeKey) {
		return placeHoursRepository.findByPlaceKey(placeKey).orElse(null);
	}

	/**
	 * 외부 전시 API 수집 → DB 적재/완성(<b>목록+상세 한 패스</b>). 목록과 상세2를 함께 받아 <b>적재 시점에 곧바로 완전한 행</b>으로 만든다:
	 * <ul>
	 *   <li>신규(externalId 미존재): 상세까지 채워 새로 적재한다.</li>
	 *   <li>기존이나 상세 미완성: 상세만 채워 완성한다(장르 등 다른 보강값은 건드리지 않는다).</li>
	 *   <li>이미 상세까지 완성: 건너뛴다(외부 상세 호출 없음 → 정상 상태에선 값이 안 바뀐다).</li>
	 * </ul>
	 * 별도 상세 백필 잡 없이 이 동기화 하나로 상세까지 채운다. 루프는 트랜잭션 밖에서 돌고 행 단위 save만 각자 트랜잭션으로 커밋해
	 * 다건 상세 API 호출을 한 트랜잭션에 오래 물지 않는다(커넥션 장기 점유 방지). 장르(AI 분류)는 호출부가
	 * {@link CatalogEnricher#enrichGenres()}로 이어서 채운다(미분류 행 = 방금 적재된 신규 전시).
	 * 인증키 미설정 시 수집 목록이 비어 0을 반환한다(외부 호출 없음).
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
		// 원천이 말한 총 건수와 절단 여부 — 현행이 파싱만 하고 버려 감지하지 못하던 사실이다.
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
			// 벤더층 기록은 도메인 적재보다 <b>먼저·무조건</b> 한다 — 기간 불량으로 스킵되는 행이야말로
			// "원천이 뭐라고 했나"의 증거가 필요한 행이기 때문이다(원본은 도메인 유효성과 생명주기가 다르다).
			archiveListResponse(data, syncedAt);
			// 원천 데이터 품질 이슈(예: 종료일<시작일)로 단건이 도메인 불변식을 어겨도 배치 전체가 중단되지 않도록,
			// 부적합 레코드는 건너뛰고 계속 적재한다. 엔티티를 건드리기 전에 걸러 dirty-flush도 방지한다.
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
				// 상세 조회 등 일시 실패는 이번 행만 건너뛰고 다음 주기에 재시도한다(불완전한 행을 적재하지 않는다).
				deferred++;
				log.warn("전시 동기화 단건 실패(externalId={}, 다음 주기 재시도): {}", data.externalId(), e.getMessage());
			}
		}
		if (skipped > 0 || completed > 0 || deferred > 0) {
			log.info("전시 동기화: 수집 {} / 신규적재 {} / 기존상세완성 {} / 기간스킵 {} / 실패연기 {}",
					collected.size(), inserted, completed, skipped, deferred);
		}
		// 같은 집계를 행으로도 남긴다 — 로그는 질의할 수 없어 추이도 회귀도 볼 수 없다.
		archiveSyncRun(run, inserted, completed, skipped, deferred);
		return inserted;
	}

	/** 실행 기록 적재. 실패해도 동기화를 깨지 않는다 — 부가 기록이 본 기능을 멈추면 이관이 서비스를 후퇴시킨다. */
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

	/**
	 * 목록 1건을 상세까지 채워 적재/완성한다. 신규는 상세를 받아 새로 적재하고, 기존 미완성 행은 상세만 채워 완성한다.
	 * 상세가 원천에 없으면(빈 응답) 목록 필드만으로 적재하되 확인 완료로 표기해 재조회를 막는다.
	 * 상세 API 일시 실패는 예외로 전파되어 호출부에서 해당 행만 연기한다. save만 트랜잭션(외부 호출은 트랜잭션 밖).
	 */
	private SyncOutcome syncListedWithDetail(CatalogExhibitionData data) {
		Exhibition existing = exhibitionRepository.findByExternalId(data.externalId()).orElse(null);
		if (existing != null) {
			if (existing.isDetailSynced()) {
				return SyncOutcome.SKIPPED; // 이미 상세까지 완성 — 재적재/재호출 없음
			}
			applyDetailOrCheck(existing);
			exhibitionRepository.save(existing);
			return SyncOutcome.COMPLETED;
		}
		Exhibition created = Exhibition.createCatalog(data.externalId(), data.title(), data.place(),
				data.startDate(), data.endDate(), data.region(), data.category(), data.posterUrl(), null, null,
				null, data.detailUrl(), data.serviceName(), data.gpsX(), data.gpsY(), data.sigungu(),
				data.realmName(), data.areaText());
		applyDetailOrCheck(created);
		exhibitionRepository.save(created);
		// 이벤트 구동 재검증(설계 §4-1): 새 전시가 기존 장소(place_hours 존재)에 들어오면 그 장소 영업시간을 재검증 enqueue한다.
		// 가드(기존 장소만·최소 간격·UK 중복)는 큐 파사드가 판단한다. place_key는 상세 도착 후에만 있으므로 여기(적재 직후)가 맞다.
		enqueueHoursRefreshBestEffort(created.getPlaceKey());
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

	/** 원천 상세2를 받아 채우거나(있음), 상세 미보유(빈 응답)면 확인 완료만 표기한다. 일시 실패는 예외로 전파된다. */
	private void applyDetailOrCheck(Exhibition exhibition) {
		String externalId = exhibition.getExternalId();
		java.util.Optional<CatalogDetailData> detail;
		try {
			detail = catalogClient.fetchDetail(externalId);
		} catch (RuntimeException e) {
			// 진행 상태(재시도)는 이제 통합 작업큐가 안다 — 상세 실패는 DETAIL_SYNC 작업으로 남겨 백오프 재시도되게 한다.
			// (현행 최대 갭 해소: 벤더 테이블의 status/attempt는 쓰기만 되고 안 읽혔다.) 예외는 그대로 전파해 이 행만 연기하는 기존 동작은 불변이다.
			enqueueDetailRetryBestEffort(externalId);
			throw e;
		}
		detail.ifPresentOrElse(exhibition::applyDetail, exhibition::markDetailChecked);
		archiveDetailOutcome(externalId, detail.orElse(null));
	}

	/**
	 * 목록 응답 원본을 벤더층에 upsert한다(신규 = first, 재수집 = seenAgain). UK(external_id)라 멱등하다.
	 * <p>
	 * <b>실패해도 동기화를 깨지 않는다</b> — 이 단계의 원본 적재는 도메인 적재와 <b>병행</b>일 뿐이고, 읽는 곳이 아직 없다.
	 * 부가 기록 때문에 본 기능이 멈추면 이관이 오히려 서비스를 후퇴시킨다({@link PlaceHoursEnricher}가 영업시간에
	 * 적용하는 원칙과 같다).
	 * <p>
	 * payload가 null이면(원본 조각을 신뢰할 수 없는 응답) 적재하지 않는다 — 원본 없는 원본 행은 의미가 없고,
	 * 잘못된 조각을 남기면 재파싱 원료가 오염된다.
	 */
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
	 * 원천에 상세가 없으면(빈 응답) 남길 원본이 없어 행을 만들지 않는다(그 사실은 {@code exhibitions.detail_synced_at}이 안다).
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

	/** 원천 데이터 기간 유효성 — 둘 다 있을 때만 시작일 ≤ 종료일. 결측은 관대하게 통과(엔티티 불변식과 동일 기준). */
	private static boolean hasValidPeriod(CatalogExhibitionData data) {
		return data.startDate() == null || data.endDate() == null || !data.startDate().isAfter(data.endDate());
	}

	/** date 지정 시 그 날짜, 아무 필터(키워드·지역·카테고리·섹션) 없으면 오늘(랜딩 기본), 그 외엔 기간 제한 없음. */
	private LocalDate resolveOngoingOn(LocalDate date, String keyword, List<ExhibitionRegion> regions,
			List<ExhibitionCategory> categories, ExhibitionSection section, LocalDate today) {
		if (date != null) {
			return date;
		}
		boolean noOtherFilter = (keyword == null || keyword.isBlank()) && regions.isEmpty()
				&& categories.isEmpty() && section == null;
		return noOtherFilter ? today : null;
	}

	/** 섹션 날짜창을 오늘·period로 계산해 조회 쿼리를 만든다. */
	private ExhibitionQuery buildQuery(String keyword, LocalDate ongoingOn, List<ExhibitionRegion> regions,
			List<ExhibitionCategory> categories, ExhibitionSection section, LocalDate today, String period,
			String sort, String cursorKey, Long cursorId, Long requesterId) {
		LocalDate from = null;
		LocalDate to = null;
		if (section == ExhibitionSection.ENDING_SOON) {
			from = today;
			to = today.plusDays(ENDING_SOON_DAYS);
		} else if (section == ExhibitionSection.OPENING_THIS_MONTH) {
			if ("week".equalsIgnoreCase(period == null ? "" : period.trim())) {
				from = today.with(DayOfWeek.MONDAY);
				to = today.with(DayOfWeek.SUNDAY);
			} else {
				from = today.withDayOfMonth(1);
				to = today.with(TemporalAdjusters.lastDayOfMonth());
			}
		}
		return new ExhibitionQuery(keyword, ongoingOn, regions, categories, section, from, to, sort,
				cursorKey, cursorId, requesterId);
	}

	private Set<Long> bookmarkedIds(Long requesterId, List<Exhibition> page) {
		if (requesterId == null || page.isEmpty()) {
			return Set.of();
		}
		List<Long> ids = page.stream().map(Exhibition::getId).toList();
		return exhibitionBookmarkRepository.findBookmarkedExhibitionIds(requesterId, ids);
	}

	/** 마지막 커서 값(정렬 컬럼값 + id)으로 다음 페이지 커서를 인코딩한다. 정렬 컬럼값이 null이면 key 없이 id만. */
	private static String encodeCursor(String sort, Exhibition last) {
		String key = switch (sort) {
			case "ending" -> last.getEndDate() == null ? null : last.getEndDate().toString();
			case "popular" -> String.valueOf(last.getOurViewCount());
			default -> last.getStartDate() == null ? null : last.getStartDate().toString();
		};
		return Cursor.of(sort, key, last.getId()).encode();
	}

	private static Comparator<Exhibition> distanceComparator(double lat, double lng) {
		return Comparator.comparingDouble((Exhibition e) -> distanceSq(e, lat, lng))
				.thenComparing(Exhibition::getId);
	}

	/** 단순 제곱 유클리드 거리(좌표 null은 최댓값 → 뒤로). 근거리 정렬 근사로 충분하다(정밀 하버사인 대신 단순화). */
	private static double distanceSq(Exhibition e, double lat, double lng) {
		if (e.getGpsX() == null || e.getGpsY() == null) {
			return Double.MAX_VALUE;
		}
		double dx = e.getGpsX() - lng;
		double dy = e.getGpsY() - lat;
		return dx * dx + dy * dy;
	}

	/** 정렬된 리스트에서 lastId 항목 다음 인덱스. 못 찾으면(데이터 변동) 0(처음부터). */
	private static int nextIndexAfter(List<Exhibition> ordered, Long lastId) {
		for (int i = 0; i < ordered.size(); i++) {
			if (ordered.get(i).getId().equals(lastId)) {
				return i + 1;
			}
		}
		return 0;
	}

	private static String normalizeKeyword(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		if (trimmed.length() < 2) {
			throw new CoreException(ErrorType.INVALID_INPUT, "검색어는 최소 2글자여야 합니다: " + raw);
		}
		return trimmed;
	}

	private static List<ExhibitionRegion> parseRegions(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty())
				.map(ExhibitionRegion::from).toList();
	}

	private static List<ExhibitionCategory> parseCategories(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty())
				.map(ExhibitionCategory::from).toList();
	}

	/** sort 코드 → 정규화(latest 기본). 미정의 값은 latest로 취급한다(커서 정렬 판별자도 이 값으로 통일). */
	private static String canonicalSort(String sort) {
		if (sort == null) {
			return "latest";
		}
		return switch (sort.trim().toLowerCase()) {
			case "ending" -> "ending";
			case "popular" -> "popular";
			case "distance" -> "distance";
			default -> "latest";
		};
	}

	private static int clampSize(Integer size) {
		if (size == null || size < 1) {
			return DEFAULT_SIZE;
		}
		return Math.min(size, MAX_SIZE);
	}
}
