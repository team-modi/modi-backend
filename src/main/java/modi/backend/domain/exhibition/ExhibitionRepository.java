package modi.backend.domain.exhibition;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Exhibition 영속화 포트(도메인 소유). 구현은 infra(DIP). soft delete된 행은 조회에서 제외한다.
 */
public interface ExhibitionRepository {

	Exhibition save(Exhibition exhibition);

	Optional<Exhibition> findById(Long id);

	/** 조건·페이지네이션으로 전시 목록 조회. CUSTOM 노출은 {@code query.requesterId}로 필터링한다. */
	Page<Exhibition> search(ExhibitionQuery query, Pageable pageable);

	/** CATALOG 동기화 upsert용 — 원천 식별자로 기존 행 조회. */
	Optional<Exhibition> findByExternalId(String externalId);

	/** 장르 초기화 백필용 — 아직 장르가 없는 CATALOG(공공데이터) 전시를 최대 {@code limit}건 조회(살아있는 행만). */
	List<Exhibition> findCatalogWithoutGenre(int limit);

	/**
	 * 홈 배너용(03_전시.md E-10) — {@code onDate}에 진행 중(startDate ≤ onDate ≤ endDate)인 CATALOG 전시를
	 * 조회수(ourViewCount) 내림차순으로 최대 {@code limit}건 조회한다(살아있는 행만).
	 */
	List<Exhibition> findOngoingCatalogTopByViews(LocalDate onDate, int limit);
}
