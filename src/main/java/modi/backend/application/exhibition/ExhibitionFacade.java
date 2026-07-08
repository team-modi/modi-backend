package modi.backend.application.exhibition;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.CatalogExhibitionData;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.domain.exhibition.ExhibitionFormat;
import modi.backend.domain.exhibition.ExhibitionQuery;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.domain.exhibition.GenreClassification;
import modi.backend.domain.exhibition.GenreClassifier;
import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;
import modi.backend.support.time.AppTime;

/**
 * 전시 유스케이스 조율(03_전시.md). load·조율·save만 하고, 상태 변경·규칙 판단은 {@link Exhibition} 메서드에 위임한다.
 * CATALOG는 외부 API({@link ExhibitionCatalogClient})로 동기화해 DB에 upsert하고, 조회/등록은 DB만 본다
 * (실시간 외부 호출 대신 수집-적재 방식 — 공공데이터 리뷰의 "야간 배치 동기화" 아키텍처).
 */
@Service
@RequiredArgsConstructor
public class ExhibitionFacade {

	private static final Logger log = LoggerFactory.getLogger(ExhibitionFacade.class);

	private final ExhibitionRepository exhibitionRepository;
	private final ExhibitionCatalogClient catalogClient;
	/** 장르 분류 전략(랜덤/AI) — 주입되는 구현은 {@code app.exhibition.genre.classifier}로 선택된다(@Primary). */
	private final GenreClassifier genreClassifier;

	/**
	 * 목록/탐색(3.3.1). 필터 미지정 시 오늘 진행 중인 전시를 기본 노출한다. 비로그인은 CATALOG만.
	 * 정렬(latest|ending|popular)은 DB 쿼리에서 처리한다 — 요청 Pageable의 sort를 매핑한 Sort로 재구성해 전달한다.
	 */
	@Transactional(readOnly = true)
	public Page<ExhibitionResult.ListItem> search(ExhibitionCriteria.Search criteria, Pageable pageable) {
		ExhibitionRegion region = criteria.region() == null ? null : ExhibitionRegion.from(criteria.region());
		ExhibitionCategory category = criteria.category() == null ? null
				: ExhibitionCategory.from(criteria.category());
		LocalDate ongoingOn = resolveOngoingOn(criteria.date(), criteria.keyword(), region, category);

		ExhibitionQuery query = new ExhibitionQuery(criteria.keyword(), ongoingOn, region, category,
				criteria.requesterId());
		Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				toSort(criteria.sort()));
		return exhibitionRepository.search(query, sortedPageable).map(ExhibitionResult.ListItem::from);
	}

	/** sort 코드 → DB 정렬. latest(기본)=시작일 최신순, ending=종료일 임박순, popular=조회수 많은순. 미정의 값은 latest로 취급. */
	private Sort toSort(String sort) {
		return switch (sort == null ? "latest" : sort) {
			case "ending" -> Sort.by(Sort.Direction.ASC, "endDate");
			case "popular" -> Sort.by(Sort.Direction.DESC, "ourViewCount");
			default -> Sort.by(Sort.Direction.DESC, "startDate");
		};
	}

	/**
	 * 상세(3.3.2). 없으면 404, 타인의 CUSTOM이면 403.
	 * CATALOG이고 아직 상세(detail2)를 수집하지 않았으면 최초 진입 시 1회 지연 수집해 캐시한다.
	 * 외부 수집 실패는 요청을 막지 않는다 — 기본 필드만으로 응답하고, detailSyncedAt이 null로 남아 다음 조회 때 재시도한다.
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
		return ExhibitionResult.Detail.from(exhibition);
	}

	/** 스냅샷/조회용 — 조회수 증가·외부 상세수집 없이 DB에서만 전시를 읽어 반환한다(기록 생성 등 내부 사용). */
	@Transactional(readOnly = true)
	public ExhibitionResult.Detail getForSnapshot(Long exhibitionId, Long requesterId) {
		Exhibition e = exhibitionRepository.findById(exhibitionId)
				.orElseThrow(() -> new CoreException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND));
		if (!e.isAccessibleBy(requesterId)) {
			throw new CoreException(ErrorType.FORBIDDEN, "타인의 개인 전시 접근: " + exhibitionId);
		}
		return ExhibitionResult.Detail.from(e);
	}

	/** 개인 전시 등록(3.3.3). 제목 필수·기간 검증은 Entity에서 수행한다. */
	@Transactional
	public ExhibitionResult.Created registerCustom(ExhibitionCriteria.CustomCreate criteria) {
		ExhibitionRegion region = criteria.region() == null ? null : ExhibitionRegion.from(criteria.region());
		ExhibitionCategory category = criteria.category() == null ? null
				: ExhibitionCategory.from(criteria.category());
		ExhibitionFormat format = criteria.format() == null ? null : ExhibitionFormat.from(criteria.format());
		// 장르는 분류기(랜덤/AI)가 부여한다. 분류기는 실패해도 예외를 던지지 않고 유효 장르를 반환하므로 등록 흐름을 깨지 않는다.
		String genreKeyword = genreClassifier.classify(new GenreClassification(criteria.title(),
				category == null ? null : category.name(), null, criteria.place(), criteria.artist(), null));
		Exhibition exhibition = Exhibition.createCustom(criteria.ownerId(), criteria.title(), criteria.place(),
				criteria.startDate(), criteria.endDate(), region, category, format, criteria.artist(),
				criteria.posterUrl(), genreKeyword);
		return ExhibitionResult.Created.from(exhibitionRepository.save(exhibition));
	}

	/**
	 * 장르 초기화 백필 — 아직 장르가 없는 CATALOG(공공데이터) 전시를 최대 {@code max}건 분류기(랜덤/AI)로 채운다.
	 * 부팅 시 {@code ExhibitionGenreInitializer}가 호출한다. 분류기가 폴백을 보장하므로 개별 실패로 중단되지 않으며,
	 * 무료 한도(429)로 일부가 랜덤 폴백되어도 다음 실행에서 다시 시도한다(장르가 채워진 행은 대상에서 빠짐).
	 *
	 * @return 이번 실행으로 장르를 부여한 전시 수
	 */
	@Transactional
	public int initGenres(int max) {
		List<Exhibition> targets = exhibitionRepository.findCatalogWithoutGenre(max);
		int classified = 0;
		for (Exhibition exhibition : targets) {
			exhibition.applyGenre(genreClassifier.classify(GenreClassification.from(exhibition)));
			exhibitionRepository.save(exhibition);
			classified++;
		}
		return classified;
	}

	/**
	 * 외부 전시 API 수집 → DB upsert(externalId 기준). 이미 있으면 카탈로그 필드 갱신, 없으면 신규 적재.
	 * 인증키 미설정 시 수집 목록이 비어 0을 반환한다(외부 호출 없음).
	 *
	 * @return 이번 동기화로 적재/갱신된 전시 수
	 */
	@Transactional
	public int syncCatalog() {
		List<CatalogExhibitionData> collected = catalogClient.fetchAll();
		int upserted = 0;
		int skipped = 0;
		for (CatalogExhibitionData data : collected) {
			// 원천 데이터 품질 이슈(예: 종료일<시작일)로 단건이 도메인 불변식을 어겨도 배치 전체가 중단되지 않도록,
			// 부적합 레코드는 건너뛰고 계속 적재한다. 엔티티를 건드리기 전에 걸러 dirty-flush도 방지한다.
			if (!hasValidPeriod(data)) {
				skipped++;
				continue;
			}
			exhibitionRepository.findByExternalId(data.externalId())
					.ifPresentOrElse(existing -> refresh(existing, data), () -> create(data));
			upserted++;
		}
		if (skipped > 0) {
			log.warn("전시 동기화: 기간 비정상 {}건 스킵(수집 {}건 중 {}건 적재)", skipped, collected.size(), upserted);
		}
		return upserted;
	}

	/** 원천 데이터 기간 유효성 — 둘 다 있을 때만 시작일 ≤ 종료일. 결측은 관대하게 통과(엔티티 불변식과 동일 기준). */
	private static boolean hasValidPeriod(CatalogExhibitionData data) {
		return data.startDate() == null || data.endDate() == null || !data.startDate().isAfter(data.endDate());
	}

	private void refresh(Exhibition existing, CatalogExhibitionData data) {
		existing.refreshCatalog(data.title(), data.place(), data.startDate(), data.endDate(), data.region(),
				data.category(), data.posterUrl(), null, null, null, data.detailUrl(), data.serviceName(),
				data.gpsX(), data.gpsY(), data.sigungu(), data.realmName(), data.areaText());
		exhibitionRepository.save(existing);
	}

	private void create(CatalogExhibitionData data) {
		exhibitionRepository.save(Exhibition.createCatalog(data.externalId(), data.title(), data.place(),
				data.startDate(), data.endDate(), data.region(), data.category(), data.posterUrl(), null, null,
				null, data.detailUrl(), data.serviceName(), data.gpsX(), data.gpsY(), data.sigungu(),
				data.realmName(), data.areaText()));
	}

	/** date 지정 시 그 날짜, 아무 필터 없으면 오늘(랜딩 기본), 그 외(keyword/region/category만)엔 기간 제한 없음. */
	private LocalDate resolveOngoingOn(LocalDate date, String keyword, ExhibitionRegion region,
			ExhibitionCategory category) {
		if (date != null) {
			return date;
		}
		boolean noOtherFilter = (keyword == null || keyword.isBlank()) && region == null && category == null;
		// 랜딩 기본 "진행중"은 한국 기준 오늘(JVM 기본 타임존은 UTC).
		return noOtherFilter ? LocalDate.now(AppTime.KST) : null;
	}
}
