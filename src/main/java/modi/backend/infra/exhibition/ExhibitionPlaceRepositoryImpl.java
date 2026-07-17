package modi.backend.infra.exhibition;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.ExhibitionPlace;
import modi.backend.domain.exhibition.ExhibitionPlaceRepository;

/** {@link ExhibitionPlaceRepository} 어댑터(DIP). 살아있는 행만 본다. */
@Repository
@RequiredArgsConstructor
public class ExhibitionPlaceRepositoryImpl implements ExhibitionPlaceRepository {

	private final ExhibitionPlaceJpaRepository jpaRepository;

	@Override
	public ExhibitionPlace save(ExhibitionPlace place) {
		return jpaRepository.save(place);
	}

	@Override
	public Optional<ExhibitionPlace> findById(Long id) {
		return jpaRepository.findById(id).filter(p -> p.getDeletedAt() == null);
	}

	@Override
	public Optional<ExhibitionPlace> findByPlaceKey(String placeKey) {
		if (placeKey == null) {
			return Optional.empty();
		}
		return jpaRepository.findByPlaceKeyAndDeletedAtIsNull(placeKey);
	}

	@Override
	public List<ExhibitionPlace> findAllByIds(Collection<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return jpaRepository.findAllById(ids).stream()
				.filter(p -> p.getDeletedAt() == null)
				.toList();
	}

	@Override
	public List<ExhibitionPlace> findPlacesNeedingHours(LocalDateTime staleBefore, int limit) {
		return jpaRepository.findPlacesNeedingHours(staleBefore, PageRequest.of(0, Math.max(1, limit)));
	}
}
