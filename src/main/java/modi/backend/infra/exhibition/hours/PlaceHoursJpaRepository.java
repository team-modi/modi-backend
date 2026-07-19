package modi.backend.infra.exhibition.hours;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.hours.PlaceHours;

public interface PlaceHoursJpaRepository extends JpaRepository<PlaceHours, Long> {

	Optional<PlaceHours> findByExhibitionPlaceId(Long exhibitionPlaceId);

	List<PlaceHours> findAllByExhibitionPlaceIdIn(Collection<Long> exhibitionPlaceIds);
}
