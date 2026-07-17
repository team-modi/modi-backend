package modi.backend.infra.exhibition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.PlaceHours;

public interface PlaceHoursJpaRepository extends JpaRepository<PlaceHours, Long> {

	Optional<PlaceHours> findByPlaceKey(String placeKey);

	List<PlaceHours> findAllByPlaceKeyIn(Collection<String> placeKeys);
}
