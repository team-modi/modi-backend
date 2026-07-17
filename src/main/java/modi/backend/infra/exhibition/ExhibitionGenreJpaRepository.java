package modi.backend.infra.exhibition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.ExhibitionGenre;

public interface ExhibitionGenreJpaRepository extends JpaRepository<ExhibitionGenre, Long> {

	Optional<ExhibitionGenre> findByExhibitionId(Long exhibitionId);

	List<ExhibitionGenre> findAllByExhibitionIdIn(Collection<Long> exhibitionIds);
}
