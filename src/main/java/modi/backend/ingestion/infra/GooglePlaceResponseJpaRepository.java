package modi.backend.ingestion.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.ingestion.domain.entity.GooglePlaceResponse;

public interface GooglePlaceResponseJpaRepository extends JpaRepository<GooglePlaceResponse, Long> {

	Optional<GooglePlaceResponse> findByExhibitionPlaceId(Long exhibitionPlaceId);
}
