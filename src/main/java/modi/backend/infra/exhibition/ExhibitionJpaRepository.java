package modi.backend.infra.exhibition;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionType;

/**
 * Spring Data JPA. 동적 필터(keyword·date·region·category·CUSTOM 노출)는
 * {@link JpaSpecificationExecutor}로 조합한다(프로젝트에 QueryDSL 미도입 → Specification 사용).
 */
public interface ExhibitionJpaRepository
		extends JpaRepository<Exhibition, Long>, JpaSpecificationExecutor<Exhibition> {

	/** soft delete된 행은 제외하고 원천 식별자로 조회(동기화 upsert용). */
	Optional<Exhibition> findByExternalIdAndDeletedAtIsNull(String externalId);

	/** 장르 미부여 CATALOG를 페이지 크기만큼 조회(장르 초기화 백필용, 살아있는 행만). */
	List<Exhibition> findByTypeAndGenreKeywordIsNullAndDeletedAtIsNull(ExhibitionType type, Pageable pageable);
}
