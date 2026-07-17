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
import modi.backend.domain.exhibition.Artist;
import modi.backend.domain.exhibition.ArtistRepository;
import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.CatalogExhibitionData;
import modi.backend.domain.exhibition.CatalogListData;
import modi.backend.domain.exhibition.CultureDetailResponse;
import modi.backend.domain.exhibition.CultureDetailResponseRepository;
import modi.backend.domain.exhibition.CultureListResponse;
import modi.backend.domain.exhibition.CultureListResponseRepository;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionArtist;
import modi.backend.domain.exhibition.ExhibitionArtistRepository;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionDetail;
import modi.backend.domain.exhibition.ExhibitionDetailRepository;
import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.domain.exhibition.ExhibitionFormat;
import modi.backend.domain.exhibition.ExhibitionGenre;
import modi.backend.domain.exhibition.ExhibitionGenreRepository;
import modi.backend.domain.exhibition.ExhibitionPlace;
import modi.backend.domain.exhibition.ExhibitionPlaceRepository;
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
import modi.backend.domain.exhibition.PlaceHours;
import modi.backend.domain.exhibition.PlaceHoursData;
import modi.backend.domain.exhibition.PlaceHoursRepository;
import modi.backend.domain.exhibition.PlaceHoursStatus;
import modi.backend.domain.exhibition.PlaceHoursVendor;
import modi.backend.domain.exhibition.SyncRun;
import modi.backend.domain.exhibition.SyncRunRepository;
import modi.backend.domain.venue.Venue;
import modi.backend.domain.venue.VenueErrorCode;
import modi.backend.domain.venue.VenueRepository;
import modi.backend.infra.record.RecordJpaRepository;
import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;
import modi.backend.support.response.Cursor;
import modi.backend.support.time.AppTime;

/**
 * 전시 유스케이스 조율(03_전시.md). load·조율·save만 하고, 상태 변경·규칙 판단은 도메인 엔티티에 위임한다.
 * 장소(name/region/gps/주소)는 {@link ExhibitionPlace}(N:1, resolve-or-create), 상세(price/description/img)는
 * {@link ExhibitionDetail}(1:1), 작가는 {@link Artist}+{@link ExhibitionArtist}(N:M) — 응답 조립 시 조인한다(API 계약 불변).
 */
@Service
@RequiredArgsConstructor
public class ExhibitionFacade {

	private static final Logger log = LoggerFactory.getLogger(ExhibitionFacade.class);
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 50;
	private static final int ENDING_SOON_DAYS = 7;
	private static final int BANNER_LIMIT = 3;

	private final ExhibitionRepository exhibitionRepository;
	private final ExhibitionCatalogClient catalogClient;
	private final ExhibitionBookmarkRepository exhibitionBookmarkRepository;
	private final VenueRepository venueRepository;
	private final RecordJpaRepository recordJpaRepository;
	private final PlaceHoursRepository placeHoursRepository;
	private final GooglePlaceResponseRepository googlePlaceResponseRepository;
	private final GenreClassifier genreClassifier;
	private final ExhibitionGenreRepository exhibitionGenreRepository;
	private final CultureListResponseRepository cultureListResponseRepository;
	private final CultureDetailResponseRepository cultureDetailResponseRepository;
	private final SyncRunRepository syncRunRepository;
	/** 전시장(N:1) — 자연키=정규화 이름. 장소·지역·gps·주소의 진실 원천이자 영업시간 정렬 기준(ADR-05·06·07). */
	private final ExhibitionPlaceRepository exhibitionPlaceRepository;
	/** 상세 satellite(1:1) — price/description/img. 연관 부재 = 미동기화(ADR-03). */
	private final ExhibitionDetailRepository exhibitionDetailRepository;
	/** 작가(정규화 이름 UK) + 조인(N:M) — CUSTOM 등록의 artist 문자열을 resolve-or-create해 조인으로 잇는다. */
	private final ArtistRepository artistRepository;
	private final ExhibitionArtistRepository exhibitionArtistRepository;

	/** 지역 필터 그룹 목록(디자인 병합 칩). 정적 enum 메타데이터라 조회 없이 변환만 한다. */
	public List<ExhibitionResult.RegionGroup> getRegionGroups() {
		return ExhibitionRegionGroup.all().stream().map(ExhibitionResult.RegionGroup::from).toList();
	}

	/**
	 * 목록/탐색(5.2). 필터 미지정 시 오늘 진행 중인 전시를 기본 노출한다. 비로그인은 CATALOG만.
	 * place·region은 전시장 조인에서, free는 상세 가격에서 조립한다.
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

		List<ExhibitionResult.ListItem> content = toListItems(page, today, criteria.requesterId());
		String nextCursor = hasNext ? encodeCursor(sort, page.get(page.size() - 1)) : null;
		long totalCount = exhibitionRepository.count(query);
		return new ExhibitionResult.ListPage(content, nextCursor, hasNext, totalCount);
	}

	/** 페이지 전시들을 장소·상세·관심 배치 조회로 조립한 목록 항목으로 변환한다(N+1 방지). */
	private List<ExhibitionResult.ListItem> toListItems(List<Exhibition> page, LocalDate today, Long requesterId) {
		if (page.isEmpty()) {
			return List.of();
		}
		Map<Long, ExhibitionPlace> placesById = placesByIdFor(page);
		Map<Long, ExhibitionDetail> detailsByExhibitionId = exhibitionDetailRepository
				.findAllByExhibitionIds(page.stream().map(Exhibition::getId).toList()).stream()
				.collect(Collectors.toMap(ExhibitionDetail::getExhibitionId, d -> d, (a, b) -> a));
		Set<Long> bookmarked = bookmarkedIds(requesterId, page);
		return page.stream().map(e -> {
			ExhibitionDetail detail = detailsByExhibitionId.get(e.getId());
			boolean free = detail != null && detail.isFree();
			return ExhibitionResult.ListItem.from(e, placesById.get(e.getExhibitionPlaceId()), today, free,
					bookmarked.contains(e.getId()));
		}).toList();
	}

	private Map<Long, ExhibitionPlace> placesByIdFor(List<Exhibition> exhibitions) {
		Set<Long> placeIds = exhibitions.stream().map(Exhibition::getExhibitionPlaceId).collect(Collectors.toSet());
		return exhibitionPlaceRepository.findAllByIds(placeIds).stream()
				.collect(Collectors.toMap(ExhibitionPlace::getId, p -> p, (a, b) -> a));
	}

	/**
	 * 홈 배너(E-10). 오늘 진행 중인 전시 중 조회수 상위 최대 3개를 노출한다. 진행 중 전시가 없으면 빈 배열.
	 */
	@Transactional(readOnly = true)
	public List<ExhibitionResult.Banner> banners() {
		List<Exhibition> rows = exhibitionRepository.findOngoingCatalogTopByViews(LocalDate.now(AppTime.KST),
				BANNER_LIMIT);
		Map<Long, ExhibitionPlace> placesById = placesByIdFor(rows);
		return rows.stream()
				.map(e -> ExhibitionResult.Banner.from(e, placesById.get(e.getExhibitionPlaceId())))
				.toList();
	}

	/**
	 * 거리순(5.2, P2 best-effort). lat·lng 필수. 좌표는 전시장(exhibition_place)에서 온다 — 후보의 장소를 배치 로드해
	 * 단순 제곱거리로 정렬(좌표 null은 뒤로)하고, 커서는 마지막 id 위치 기준으로 슬라이스한다.
	 */
	private ExhibitionResult.ListPage searchByDistance(ExhibitionCriteria.Search criteria, LocalDate today,
			int size, ExhibitionQuery query) {
		if (criteria.lat() == null || criteria.lng() == null) {
			throw new CoreException(ErrorType.INVALID_INPUT, "거리순 정렬은 lat·lng가 필요합니다.");
		}
		double lat = criteria.lat();
		double lng = criteria.lng();
		List<Exhibition> candidates = exhibitionRepository.searchAll(query);
		Map<Long, ExhibitionPlace> placesById = placesByIdFor(candidates);
		List<Exhibition> ordered = candidates.stream()
				.sorted(distanceComparator(placesById, lat, lng))
				.toList();

		Cursor cursor = Cursor.decode(criteria.cursor(), "distance").orElse(null);
		int start = cursor == null ? 0 : nextIndexAfter(ordered, cursor.lastId());
		int end = Math.min(start + size, ordered.size());
		List<Exhibition> page = start >= ordered.size() ? List.of() : ordered.subList(start, end);
		boolean hasNext = end < ordered.size();

		List<ExhibitionResult.ListItem> content = toListItems(page, today, criteria.requesterId());
		String nextCursor = null;
		if (hasNext) {
			Exhibition last = page.get(page.size() - 1);
			nextCursor = Cursor.of("distance",
					String.valueOf(distanceSq(placesById.get(last.getExhibitionPlaceId()), lat, lng)), last.getId())
					.encode();
		}
		return new ExhibitionResult.ListPage(content, nextCursor, hasNext, ordered.size());
	}

	/**
	 * 상세(5.3). 없으면 404, 타인의 CUSTOM이면 403. CATALOG 최초 진입 시 상세를 1회 지연 수집해 캐시한다(상세 satellite upsert).
	 * place·operatingHours·artists는 요청 시 조인해 조립한다.
	 */
	@Transactional
	public ExhibitionResult.Detail getDetail(ExhibitionCriteria.Detail criteria) {
		Exhibition exhibition = exhibitionRepository.findById(criteria.exhibitionId())
				.orElseThrow(() -> new CoreException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND));
		if (!exhibition.isAccessibleBy(criteria.requesterId())) {
			throw new CoreException(ErrorType.FORBIDDEN, "타인의 개인 전시 접근: " + criteria.exhibitionId());
		}
		if (exhibition.isCatalog() && !exhibitionDetailRepository.existsByExhibitionId(exhibition.getId())) {
			try {
				catalogClient.fetchDetail(exhibition.getExternalId())
						.ifPresent(d -> applyCatalogDetail(exhibition, d, LocalDateTime.now()));
			} catch (CoreException ex) {
				// 외부 실패 시 base 필드만 반환 — 상세행이 없어 다음 조회에서 재시도된다.
			}
		}
		exhibition.increaseView();
		exhibitionRepository.save(exhibition);
		Long requesterId = criteria.requesterId();
		boolean bookmarked = requesterId != null
				&& exhibitionBookmarkRepository.existsActive(requesterId, exhibition.getId());
		boolean recorded = requesterId != null
				&& recordJpaRepository.existsByUserIdAndExhibitionIdAndDeletedAtIsNull(requesterId, exhibition.getId());
		return assembleDetail(exhibition, bookmarked, recorded);
	}

	/** 스냅샷/조회용 — 조회수 증가·외부 상세수집·개인화 없이 DB에서만 전시를 읽어 반환한다(기록 생성 등 내부 사용). */
	@Transactional(readOnly = true)
	public ExhibitionResult.Detail getForSnapshot(Long exhibitionId, Long requesterId) {
		Exhibition e = exhibitionRepository.findById(exhibitionId)
				.orElseThrow(() -> new CoreException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND));
		if (!e.isAccessibleBy(requesterId)) {
			throw new CoreException(ErrorType.FORBIDDEN, "타인의 개인 전시 접근: " + exhibitionId);
		}
		return assembleDetail(e, false, false);
	}

	/** 상세 응답 조립 — 장소·상세·영업시간·작가·장르를 각 저장소에서 읽어 하나로 모은다. */
	private ExhibitionResult.Detail assembleDetail(Exhibition exhibition, boolean bookmarked, boolean recorded) {
		ExhibitionPlace place = exhibitionPlaceRepository.findById(exhibition.getExhibitionPlaceId()).orElse(null);
		ExhibitionDetail detail = exhibitionDetailRepository.findByExhibitionId(exhibition.getId()).orElse(null);
		PlaceHours placeHours = place == null ? null
				: placeHoursRepository.findByExhibitionPlaceId(place.getId()).orElse(null);
		List<String> artistNames = exhibitionArtistRepository.findArtistNames(exhibition.getId());
		return ExhibitionResult.Detail.from(exhibition, place, detail, placeHours, artistNames,
				genreOf(exhibition.getId()), bookmarked, recorded);
	}

	private ExhibitionGenre genreOf(Long exhibitionId) {
		return exhibitionGenreRepository.findByExhibitionId(exhibitionId).orElse(null);
	}

	/**
	 * 개인 전시 등록(5.4). 제목 필수·기간·개인전 작가 검증은 Entity에서. 전시장은 resolve-or-create(정규화 이름)로 확정해
	 * {@code exhibition_place_id NOT NULL}을 지탱하고, 작가 문자열은 resolve-or-create + 조인으로 잇는다.
	 */
	@Transactional
	public ExhibitionResult.Created registerCustom(ExhibitionCriteria.CustomCreate criteria) {
		ExhibitionRegion region = criteria.region() == null ? null : ExhibitionRegion.from(criteria.region());
		ExhibitionCategory category = criteria.category() == null ? null
				: ExhibitionCategory.from(criteria.category());
		ExhibitionFormat format = criteria.format() == null ? null : ExhibitionFormat.from(criteria.format());
		String placeName = criteria.place();
		if (criteria.venueId() != null) {
			Venue venue = venueRepository.findById(criteria.venueId())
					.orElseThrow(() -> new CoreException(VenueErrorCode.VENUE_NOT_FOUND));
			placeName = venue.getName();
			if (region == null) {
				region = venue.getRegion();
			}
		}
		// place·venueId 모두 없으면 장소 미상 센티넬로 수렴한다(exhibition_place_id NOT NULL 지탱) — 제목만 등록도 그대로 동작한다.
		ExhibitionPlace place = resolvePlace(placeName, region, null, null, null);
		// 장르: 사용자가 직접 고르면 그 값(provider=USER), 미지정이면 분류기(랜덤/AI)가 부여한다(실패해도 유효 장르 반환).
		GenreResult genre;
		if (criteria.genreKeyword() != null && !criteria.genreKeyword().isBlank()) {
			if (!GenreKeyword.contains(criteria.genreKeyword())) {
				throw new CoreException(ErrorType.INVALID_INPUT, "정의되지 않은 장르 키워드: " + criteria.genreKeyword());
			}
			genre = GenreResult.user(criteria.genreKeyword());
		} else {
			genre = genreClassifier.classify(new GenreClassification(criteria.title(),
					category == null ? null : category.name(), null, placeName, criteria.artist(), null));
		}
		Exhibition exhibition = Exhibition.createCustom(criteria.ownerId(), criteria.title(), place.getId(),
				criteria.startDate(), criteria.endDate(), category, format, criteria.artist(), criteria.posterUrl());
		Exhibition saved = exhibitionRepository.save(exhibition);
		linkArtist(saved.getId(), criteria.artist());
		exhibitionGenreRepository.save(ExhibitionGenre.create(saved.getId(), genre.genreKeyword(),
				genre.provider(), genre.model(), LocalDateTime.now()));
		return ExhibitionResult.Created.from(saved);
	}

	/** 전시장 resolve-or-create — 정규화 이름 기준 upsert. 기존 행이면 비어 있던 신원 필드만 보강한다(ADR-07). */
	private ExhibitionPlace resolvePlace(String name, ExhibitionRegion region, String sigungu, Double gpsX,
			Double gpsY) {
		String placeKey = modi.backend.domain.exhibition.PlaceKey.of(name);
		return exhibitionPlaceRepository.findByPlaceKey(placeKey)
				.map(existing -> {
					existing.enrichIdentity(region, sigungu, gpsX, gpsY);
					return exhibitionPlaceRepository.save(existing);
				})
				.orElseGet(() -> exhibitionPlaceRepository
						.save(ExhibitionPlace.createFromList(name, region, sigungu, gpsX, gpsY)));
	}

	/** 작가 문자열을 resolve-or-create(정규화 이름 UK)해 전시와 조인(멱등)한다. 이름이 비면 건너뛴다. */
	private void linkArtist(Long exhibitionId, String rawArtist) {
		String normalized = Artist.normalize(rawArtist);
		if (normalized == null) {
			return;
		}
		Artist artist = artistRepository.findByName(normalized)
				.orElseGet(() -> artistRepository.save(Artist.create(normalized)));
		if (!exhibitionArtistRepository.existsByExhibitionIdAndArtistId(exhibitionId, artist.getId())) {
			exhibitionArtistRepository.save(ExhibitionArtist.of(exhibitionId, artist.getId()));
		}
	}

	/**
	 * 개인 전시(CUSTOM) 동반 삭제 — 본인이 등록한 CUSTOM만 soft-delete(공용 CATALOG·타인 전시·이미 삭제된 전시는 무시), 멱등.
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
		Map<Long, ExhibitionDetail> detailsByExhibitionId = exhibitionDetailRepository
				.findAllByExhibitionIds(rows.stream().map(Exhibition::getId).toList()).stream()
				.collect(Collectors.toMap(ExhibitionDetail::getExhibitionId, d -> d, (a, b) -> a));
		return rows.stream().map(e -> {
			ExhibitionPlace place = placesById.get(e.getExhibitionPlaceId());
			ExhibitionDetail detail = detailsByExhibitionId.get(e.getId());
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
		Map<Long, ExhibitionGenre> genresById = exhibitionGenreRepository.findAllByExhibitionIds(ids).stream()
				.collect(Collectors.toMap(ExhibitionGenre::getExhibitionId, g -> g));
		int applied = 0;
		for (int i = 0; i < targets.size(); i++) {
			GenreResult result = i < results.size() ? results.get(i) : null;
			Exhibition exhibition = exhibitionsById.get(targets.get(i).exhibitionId());
			if (result == null || exhibition == null) {
				continue;
			}
			saveCanonicalGenre(exhibition.getId(), result, genresById.get(exhibition.getId()), now);
			applied++;
		}
		return applied;
	}

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
		savePlaceHours(placeId, formatted, PlaceHoursStatus.of(data, formatted), vendor, now);
	}

	/** 조회가 전송 오류로 실패했음을 정준층에 남긴다(재시도 대상). 표시값·동기화 시각은 지키지 않아 다음 주기 재시도가 유지된다. */
	@Transactional
	public void recordVenueHoursFailure(PlaceHoursTarget target, PlaceHoursVendor vendor) {
		Long placeId = target.exhibitionPlaceId();
		try {
			placeHoursRepository.findByExhibitionPlaceId(placeId)
					.ifPresentOrElse(row -> {
						row.recordFailure(vendor);
						placeHoursRepository.save(row);
					}, () -> placeHoursRepository.save(
							PlaceHours.first(placeId, null, PlaceHoursStatus.FAILED, vendor, null)));
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

	/** 정준층 upsert — 없으면 생성, 있으면 갱신(영업시간은 바뀌는 값이라 덮어쓰기가 정상 동작이다). */
	private void savePlaceHours(Long placeId, String formatted, PlaceHoursStatus status, PlaceHoursVendor vendor,
			LocalDateTime now) {
		if (placeId == null) {
			return;
		}
		placeHoursRepository.findByExhibitionPlaceId(placeId)
				.ifPresentOrElse(row -> {
					row.refresh(formatted, status, vendor, now);
					placeHoursRepository.save(row);
				}, () -> placeHoursRepository.save(PlaceHours.first(placeId, formatted, status, vendor, now)));
	}

	/**
	 * 외부 전시 API 수집 → DB 적재/완성(목록+상세 한 패스). 신규는 전시장을 resolve-or-create해 상세까지 채워 적재하고,
	 * 기존 미완성은 상세만 채운다. 루프는 트랜잭션 밖, 행 단위 save만 각자 트랜잭션으로 커밋한다.
	 *
	 * @return 이번 동기화로 새로 적재된 전시 수(기존 행 상세 완성 건은 제외)
	 */
	public int syncCatalog() {
		LocalDateTime syncedAt = LocalDateTime.now();
		SyncRun run = SyncRun.started(syncedAt);
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
			if (exhibitionDetailRepository.existsByExhibitionId(existing.getId())) {
				return SyncOutcome.SKIPPED;
			}
			applyDetailOrCheck(existing);
			return SyncOutcome.COMPLETED;
		}
		// 신규는 상세를 <b>먼저</b> 받아본다 — 상세 호출이 일시 실패하면 아무것도 적재하지 않고 이 행만 다음 주기로 연기한다
		// (불완전한 행·전시장만 남기지 않는다). 상세가 성공/빈 응답일 때만 전시장 resolve + 전시·상세 적재로 진행한다.
		java.util.Optional<CatalogDetailData> detail = fetchDetailDeferring(data.externalId());
		ExhibitionPlace place = resolvePlace(data.place(), data.region(), data.sigungu(), data.gpsX(), data.gpsY());
		Exhibition saved = exhibitionRepository.save(Exhibition.createCatalog(data.externalId(), data.title(),
				place.getId(), data.startDate(), data.endDate(), data.category(), data.posterUrl(), data.detailUrl(),
				data.serviceName()));
		LocalDateTime now = LocalDateTime.now();
		detail.ifPresentOrElse(d -> applyCatalogDetail(saved, d, now), () -> saveCheckedDetail(saved.getId(), now));
		archiveDetailOutcome(data.externalId(), detail.orElse(null));
		return SyncOutcome.INSERTED;
	}

	/** 원천 상세2를 받아 상세를 채우거나(있음), 상세 미보유(빈 응답)면 확인 완료행만 남긴다. 일시 실패는 예외로 전파된다(기존 행 완성 경로). */
	private void applyDetailOrCheck(Exhibition exhibition) {
		java.util.Optional<CatalogDetailData> detail = fetchDetailDeferring(exhibition.getExternalId());
		LocalDateTime now = LocalDateTime.now();
		detail.ifPresentOrElse(d -> applyCatalogDetail(exhibition, d, now),
				() -> saveCheckedDetail(exhibition.getId(), now));
		archiveDetailOutcome(exhibition.getExternalId(), detail.orElse(null));
	}

	/** 상세를 조회한다. 일시 실패면 벤더층에 "시도했고 실패했다"를 남기고 예외를 전파해 호출부가 이 행만 연기하게 한다. */
	private java.util.Optional<CatalogDetailData> fetchDetailDeferring(String externalId) {
		try {
			return catalogClient.fetchDetail(externalId);
		} catch (RuntimeException e) {
			archiveDetailFailure(externalId);
			throw e;
		}
	}

	/** 상세 값을 상세 satellite에 upsert하고, 전시장 보강 필드(주소/전화/홈페이지)를 채운다. */
	private void applyCatalogDetail(Exhibition exhibition, CatalogDetailData d, LocalDateTime now) {
		exhibitionDetailRepository.findByExhibitionId(exhibition.getId())
				.ifPresentOrElse(row -> {
					row.update(d.price(), d.description(), d.imgUrl(), now);
					exhibitionDetailRepository.save(row);
				}, () -> exhibitionDetailRepository.save(
						ExhibitionDetail.create(exhibition.getId(), d.price(), d.description(), d.imgUrl(), now)));
		exhibitionPlaceRepository.findById(exhibition.getExhibitionPlaceId()).ifPresent(place -> {
			place.enrichDetail(d.placeAddr(), d.phone(), d.placeUrl());
			exhibitionPlaceRepository.save(place);
		});
	}

	/** 상세를 확인했으나 원천에 값이 없음 — 확인 완료행만 남겨 재조회 대상에서 빠지게 한다(멱등). */
	private void saveCheckedDetail(Long exhibitionId, LocalDateTime now) {
		if (!exhibitionDetailRepository.existsByExhibitionId(exhibitionId)) {
			exhibitionDetailRepository.save(ExhibitionDetail.markChecked(exhibitionId, now));
		}
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

	private void archiveDetailOutcome(String externalId, CatalogDetailData data) {
		try {
			CultureDetailResponse existing = cultureDetailResponseRepository.findByExternalId(externalId).orElse(null);
			if (existing == null) {
				cultureDetailResponseRepository.save(data == null
						? CultureDetailResponse.noData(externalId)
						: CultureDetailResponse.succeeded(externalId, data.payload()));
				return;
			}
			if (data == null) {
				existing.recordNoData();
			} else {
				existing.recordSucceeded(data.payload());
			}
			cultureDetailResponseRepository.save(existing);
		} catch (RuntimeException e) {
			log.warn("상세 원본 적재 실패(externalId={}, 동기화는 계속): {}", externalId, e.getMessage());
		}
	}

	private void archiveDetailFailure(String externalId) {
		try {
			cultureDetailResponseRepository.findByExternalId(externalId)
					.ifPresentOrElse(row -> {
						row.recordFailed();
						cultureDetailResponseRepository.save(row);
					}, () -> cultureDetailResponseRepository.save(CultureDetailResponse.failed(externalId)));
		} catch (RuntimeException e) {
			log.warn("상세 실패 기록 실패(externalId={}, 동기화는 계속): {}", externalId, e.getMessage());
		}
	}

	private static boolean hasValidPeriod(CatalogExhibitionData data) {
		return data.startDate() == null || data.endDate() == null || !data.startDate().isAfter(data.endDate());
	}

	private LocalDate resolveOngoingOn(LocalDate date, String keyword, List<ExhibitionRegion> regions,
			List<ExhibitionCategory> categories, ExhibitionSection section, LocalDate today) {
		if (date != null) {
			return date;
		}
		boolean noOtherFilter = (keyword == null || keyword.isBlank()) && regions.isEmpty()
				&& categories.isEmpty() && section == null;
		return noOtherFilter ? today : null;
	}

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

	private static String encodeCursor(String sort, Exhibition last) {
		String key = switch (sort) {
			case "ending" -> last.getEndDate() == null ? null : last.getEndDate().toString();
			case "popular" -> String.valueOf(last.getOurViewCount());
			default -> last.getStartDate() == null ? null : last.getStartDate().toString();
		};
		return Cursor.of(sort, key, last.getId()).encode();
	}

	private static Comparator<Exhibition> distanceComparator(Map<Long, ExhibitionPlace> placesById, double lat,
			double lng) {
		return Comparator.comparingDouble((Exhibition e) -> distanceSq(placesById.get(e.getExhibitionPlaceId()), lat,
				lng)).thenComparing(Exhibition::getId);
	}

	/** 단순 제곱 유클리드 거리(좌표 null은 최댓값 → 뒤로). 좌표는 전시장에서 온다. */
	private static double distanceSq(ExhibitionPlace place, double lat, double lng) {
		if (place == null || place.getGpsX() == null || place.getGpsY() == null) {
			return Double.MAX_VALUE;
		}
		double dx = place.getGpsX() - lng;
		double dy = place.getGpsY() - lat;
		return dx * dx + dy * dy;
	}

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
