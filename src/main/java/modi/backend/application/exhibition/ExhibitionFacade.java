package modi.backend.application.exhibition;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.bookmark.ExhibitionBookmarkRepository;
import modi.backend.domain.exhibition.CatalogExhibitionData;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.domain.exhibition.ExhibitionFormat;
import modi.backend.domain.exhibition.ExhibitionQuery;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.domain.exhibition.ExhibitionSection;
import modi.backend.domain.exhibition.GenreClassification;
import modi.backend.domain.exhibition.GenreClassifier;
import modi.backend.domain.exhibition.GenreKeyword;
import modi.backend.domain.exhibition.PlaceHoursData;
import modi.backend.domain.exhibition.PlaceHoursSnapshot;
import modi.backend.domain.exhibition.PlaceHoursSnapshotRepository;
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
	private final PlaceHoursSnapshotRepository placeHoursSnapshotRepository;
	/** 장르 분류 전략(랜덤/AI) — 주입되는 구현은 {@code app.exhibition.genre.classifier}로 선택된다(@Primary). */
	private final GenreClassifier genreClassifier;

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
		return ExhibitionResult.Detail.from(exhibition, bookmarked, recorded);
	}

	/** 스냅샷/조회용 — 조회수 증가·외부 상세수집·개인화 없이 DB에서만 전시를 읽어 반환한다(기록 생성 등 내부 사용). */
	@Transactional(readOnly = true)
	public ExhibitionResult.Detail getForSnapshot(Long exhibitionId, Long requesterId) {
		Exhibition e = exhibitionRepository.findById(exhibitionId)
				.orElseThrow(() -> new CoreException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND));
		if (!e.isAccessibleBy(requesterId)) {
			throw new CoreException(ErrorType.FORBIDDEN, "타인의 개인 전시 접근: " + exhibitionId);
		}
		return ExhibitionResult.Detail.from(e, false, false);
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
		// 장르: 사용자가 장르 시트에서 직접 고르면 그 값(마스터 검증), 미지정이면 분류기(랜덤/AI)가 부여한다.
		// 분류기는 실패해도 예외를 던지지 않고 유효 장르를 반환하므로 등록 흐름을 깨지 않는다.
		String genreKeyword;
		if (criteria.genreKeyword() != null && !criteria.genreKeyword().isBlank()) {
			if (!GenreKeyword.contains(criteria.genreKeyword())) {
				throw new CoreException(ErrorType.INVALID_INPUT, "정의되지 않은 장르 키워드: " + criteria.genreKeyword());
			}
			genreKeyword = criteria.genreKeyword();
		} else {
			genreKeyword = genreClassifier.classify(new GenreClassification(criteria.title(),
					category == null ? null : category.name(), null, place, criteria.artist(), null));
		}
		Exhibition exhibition = Exhibition.createCustom(criteria.ownerId(), criteria.title(), place,
				criteria.startDate(), criteria.endDate(), region, category, format, criteria.artist(),
				criteria.posterUrl(), genreKeyword);
		return ExhibitionResult.Created.from(exhibitionRepository.save(exhibition));
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
	 * 장르 백필 1배치 — 아직 장르가 없는 CATALOG(공공데이터) 전시를 최대 {@code max}건 <b>한 번의 AI 호출(배치)</b>로 분류한다.
	 * {@link CatalogEnricher}가 이 메서드를 미분류가 소진될 때까지 반복 호출해 전량을 채운다(배치당 1콜 → 273건도 몇 콜로).
	 * 분류기가 폴백을 보장하므로 개별 실패로 중단되지 않으며, 429로 일부가 랜덤 폴백되어도 다음 주기에 다시 시도한다
	 * (장르가 채워진 행은 대상에서 빠져 멱등 — 반복 실행돼도 신규 행만 AI를 태운다).
	 *
	 * @return 이번 배치로 장르를 부여한 전시 수(0이면 미분류 없음 → 소진)
	 */
	@Transactional
	public int initGenres(int max) {
		List<Exhibition> targets = exhibitionRepository.findCatalogWithoutGenre(max);
		if (targets.isEmpty()) {
			return 0;
		}
		// 전시마다 호출하지 않고 한 번의 AI 호출(배치)로 전부 분류한다 — 무료 한도 429 폭주·부팅 지연 방지.
		List<GenreClassification> inputs = targets.stream().map(GenreClassification::from).toList();
		List<String> genres = genreClassifier.classifyAll(inputs);
		for (int i = 0; i < targets.size(); i++) {
			Exhibition exhibition = targets.get(i);
			exhibition.applyGenre(i < genres.size() ? genres.get(i) : null);
			exhibitionRepository.save(exhibition);
		}
		return targets.size();
	}

	/** 영업시간 보강 시작 시 구글 응답 스테이징 전체를 비운다("호출 시 초기화"). */
	@Transactional
	public void resetPlaceHoursSnapshots() {
		placeHoursSnapshotRepository.deleteAllSnapshots();
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
	 * 한 장소의 조회 결과를 반영한다(장소 단위 트랜잭션): 원본 스테이징 적재 + 그 장소 전시들에 표시값 저장.
	 * {@code data}가 null(미발견)이면 스테이징은 남기지 않고, 전시엔 {@code formatted=null}로 값은 비우되 조회 시각만 남긴다(재조회 백오프).
	 */
	@Transactional
	public void applyVenueHours(PlaceHoursTarget target, PlaceHoursData data, String formatted,
			java.time.LocalDateTime now) {
		if (data != null) {
			placeHoursSnapshotRepository.save(PlaceHoursSnapshot.of(data, target.placeAddr(), now));
		}
		for (Exhibition e : exhibitionRepository.findAllActiveByIds(target.exhibitionIds())) {
			e.applyOperatingHours(formatted, now);
			exhibitionRepository.save(e);
		}
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
		List<CatalogExhibitionData> collected = catalogClient.fetchAll();
		int inserted = 0;
		int completed = 0;
		int skipped = 0;
		int deferred = 0;
		for (CatalogExhibitionData data : collected) {
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
		return inserted;
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
		return SyncOutcome.INSERTED;
	}

	/** 원천 상세2를 받아 채우거나(있음), 상세 미보유(빈 응답)면 확인 완료만 표기한다. 일시 실패는 예외로 전파된다. */
	private void applyDetailOrCheck(Exhibition exhibition) {
		catalogClient.fetchDetail(exhibition.getExternalId())
				.ifPresentOrElse(exhibition::applyDetail, exhibition::markDetailChecked);
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
