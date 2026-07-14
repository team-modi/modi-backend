package modi.backend.domain.exhibition;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Exhibition 영속화 포트(도메인 소유). 구현은 infra(DIP). soft delete된 행은 조회에서 제외한다.
 * 목록은 커서(키셋) 페이지네이션 — {@code searchSlice}(정렬+경계 적용)와 {@code count}(경계 없는 전체 건수)로 분리한다.
 */
public interface ExhibitionRepository {

	Exhibition save(Exhibition exhibition);

	Optional<Exhibition> findById(Long id);

	/**
	 * 키셋 한 페이지 조회 — {@code query.sort} 순서로 정렬하고 {@code cursorKey/cursorId} 이후 행만 본다.
	 * hasNext 판단을 위해 {@code limitPlusOne}(=size+1)개까지 가져온다. CUSTOM 노출은 {@code requesterId}로 필터링.
	 */
	List<Exhibition> searchSlice(ExhibitionQuery query, int limitPlusOne);

	/** 커서 경계를 뺀 필터 전체 건수(totalCount용). */
	long count(ExhibitionQuery query);

	/** 필터에 맞는 후보 전체(정렬·커서 미적용). 거리순처럼 앱 레이어에서 정렬·페이징하는 경로용. */
	List<Exhibition> searchAll(ExhibitionQuery query);

	/** CATALOG 동기화 upsert용 — 원천 식별자로 기존 행 조회. */
	Optional<Exhibition> findByExternalId(String externalId);

	/** 살아있는 전시들을 id 집합으로 일괄 조회(관심 전시 목록의 벌크 로드용). 정렬·순서 보장 없음. 빈 입력이면 빈 목록. */
	List<Exhibition> findAllActiveByIds(Collection<Long> ids);

	/** 장르 초기화 백필용 — 아직 장르가 없는 CATALOG(공공데이터) 전시를 최대 {@code limit}건 조회(살아있는 행만). */
	List<Exhibition> findCatalogWithoutGenre(int limit);

	/**
	 * 영업시간 보강 대상 — 주소(placeAddr)가 있고 아직 조회 안 했거나({@code operatingHoursSyncedAt IS NULL})
	 * {@code staleBefore}보다 오래 전에 조회된 CATALOG 전시를 최대 {@code limit}건 조회(살아있는 행만).
	 * 같은 장소를 묶기 쉽도록 placeAddr·id 순으로 정렬해 반환한다.
	 */
	List<Exhibition> findCatalogNeedingOperatingHours(LocalDateTime staleBefore, int limit);

	/** 설명 재파싱용 — 설명이 있는 CATALOG 전시를 모두 조회(살아있는 행만). 재파싱 대상 판단은 호출부(멱등). */
	List<Exhibition> findCatalogWithDescription();

	/**
	 * 홈 배너용(03_전시.md E-10) — {@code onDate}에 진행 중(startDate ≤ onDate ≤ endDate)인 CATALOG 전시를
	 * 조회수(ourViewCount) 내림차순으로 최대 {@code limit}건 조회한다(살아있는 행만).
	 */
	List<Exhibition> findOngoingCatalogTopByViews(LocalDate onDate, int limit);
}
