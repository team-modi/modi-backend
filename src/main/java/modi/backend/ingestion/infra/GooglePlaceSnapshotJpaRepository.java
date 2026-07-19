package modi.backend.ingestion.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.ingestion.domain.entity.GooglePlaceSnapshot;

public interface GooglePlaceSnapshotJpaRepository extends JpaRepository<GooglePlaceSnapshot, Long> {

	Optional<GooglePlaceSnapshot> findByExhibitionPlaceId(Long exhibitionPlaceId);
}
