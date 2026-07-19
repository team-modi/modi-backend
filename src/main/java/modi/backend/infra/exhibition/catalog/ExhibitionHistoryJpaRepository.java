package modi.backend.infra.exhibition.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.catalog.ExhibitionHistory;

public interface ExhibitionHistoryJpaRepository extends JpaRepository<ExhibitionHistory, Long> {

	List<ExhibitionHistory> findByExhibitionIdOrderByEditedAtAscIdAsc(Long exhibitionId);
}
