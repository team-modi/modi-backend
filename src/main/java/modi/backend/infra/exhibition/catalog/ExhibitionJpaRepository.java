package modi.backend.infra.exhibition.catalog;

import modi.backend.domain.exhibition.catalog.ExhibitionGenre;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import modi.backend.domain.exhibition.catalog.Exhibition;
import modi.backend.domain.exhibition.catalog.ExhibitionType;

/**
 * Spring Data JPA. 동적 필터(keyword·date·region·category·CUSTOM 노출)는
 * {@link JpaSpecificationExecutor}로 조합한다(프로젝트에 QueryDSL 미도입 → Specification 사용).
 */
public interface ExhibitionJpaRepository
		extends JpaRepository<Exhibition, Long>, JpaSpecificationExecutor<Exhibition> {

	/** soft delete된 행은 제외하고 원천 식별자로 조회(동기화 upsert용). */
	Optional<Exhibition> findByExternalIdAndDeletedAtIsNull(String externalId);

	/**
	 * 장르 미부여 CATALOG를 페이지 크기만큼 조회(장르 초기화 백필용, 살아있는 행만).
	 * <p>
	 * "미부여" 판정은 정준층({@code exhibition_genre}) 행의 부재다 — 기존의 {@code genre_keyword IS NULL}이 아니다
	 * (이관 2단계-b, 읽기 전환). 이렇게 해야 provider가 남고, 랜덤 폴백분만 골라 재분류하는 길이 열린다.
	 * 전환 전 이미 분류돼 있던 행은 V21이 provider='UNKNOWN'으로 백필해 대상에서 빠진다 — 백필이 없으면
	 * 기존 전시 전량이 재분류 대상이 되어 운영에서 AI 실호출이 대량 발생한다.
	 * <p>
	 * 다른 엔티티의 행 부재 조건이라 파생 쿼리로 표현할 수 없어 JPQL {@code not exists}로 명시한다.
	 */
	@org.springframework.data.jpa.repository.Query("""
			select e from Exhibition e
			where e.type = :type and e.deletedAt is null
			  and not exists (select 1 from ExhibitionGenre g where g.exhibitionId = e.id)""")
	List<Exhibition> findCatalogWithoutCanonicalGenre(
			@org.springframework.data.repository.query.Param("type") ExhibitionType type, Pageable pageable);

	/**
	 * 홈 배너용 — 진행 중(startDate ≤ onDate ≤ endDate)인 CATALOG를 조회수 내림차순으로 페이지 크기만큼 조회(살아있는 행만).
	 * 진행 중 조건은 두 날짜 파라미터에 동일한 오늘 값을 넘겨 표현한다.
	 */
	List<Exhibition> findByTypeAndStartDateLessThanEqualAndEndDateGreaterThanEqualAndDeletedAtIsNullOrderByOurViewCountDesc(
			ExhibitionType type, java.time.LocalDate startOnOrBefore, java.time.LocalDate endOnOrAfter,
			Pageable pageable);

	// ── 관리자 콘솔 전용 ───────────────────────────────

	/** 전체 전시 수(살아있는). */
	long countByDeletedAtIsNull();

	/** 타입별 전시 수(CATALOG/CUSTOM 구분 — 대시보드용). */
	long countByTypeAndDeletedAtIsNull(ExhibitionType type);

	/** 여러 전시를 ID로 조회(사용자 상세의 북마크/전시활동 제목 표시용, 살아있는 행만). */
	List<Exhibition> findByIdInAndDeletedAtIsNull(java.util.Collection<Long> ids);

	/** 여러 원천 식별자로 CATALOG 전시를 일괄 조회(아웃박스 대상 해소용, 살아있는 행만). */
	List<Exhibition> findByTypeAndExternalIdInAndDeletedAtIsNull(ExhibitionType type,
			java.util.Collection<String> externalIds);
}
