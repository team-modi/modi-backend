package modi.backend.infra.exhibition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.PlaceHours;
import modi.backend.domain.exhibition.PlaceHoursRepository;

@Repository
@RequiredArgsConstructor
public class PlaceHoursRepositoryImpl implements PlaceHoursRepository {

	private final PlaceHoursJpaRepository jpaRepository;

	@Override
	public PlaceHours save(PlaceHours placeHours) {
		return jpaRepository.save(placeHours);
	}

	@Override
	public Optional<PlaceHours> findByPlaceKey(String placeKey) {
		if (placeKey == null) {
			return Optional.empty();
		}
		return jpaRepository.findByPlaceKey(placeKey);
	}

	@Override
	public List<PlaceHours> findAllByPlaceKeys(Collection<String> placeKeys) {
		// 빈 컬렉션에 IN () 을 던지면 DB마다 동작이 갈린다 — 호출부가 방어하지 않아도 되게 여기서 막는다.
		if (placeKeys == null || placeKeys.isEmpty()) {
			return List.of();
		}
		return jpaRepository.findAllByPlaceKeyIn(placeKeys);
	}
}
