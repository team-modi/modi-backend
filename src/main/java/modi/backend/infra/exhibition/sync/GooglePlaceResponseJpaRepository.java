package modi.backend.infra.exhibition.sync;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.sync.entity.GooglePlaceResponse;

public interface GooglePlaceResponseJpaRepository extends JpaRepository<GooglePlaceResponse, Long> {

	Optional<GooglePlaceResponse> findByExhibitionPlaceId(Long exhibitionPlaceId);
}
