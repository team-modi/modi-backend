package modi.backend.infra.exhibition;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.GooglePlaceResponse;

public interface GooglePlaceResponseJpaRepository extends JpaRepository<GooglePlaceResponse, Long> {

	Optional<GooglePlaceResponse> findByPlaceKey(String placeKey);
}
