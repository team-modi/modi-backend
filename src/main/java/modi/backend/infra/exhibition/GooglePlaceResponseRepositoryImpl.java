package modi.backend.infra.exhibition;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.GooglePlaceResponse;
import modi.backend.domain.exhibition.GooglePlaceResponseRepository;

@Repository
@RequiredArgsConstructor
public class GooglePlaceResponseRepositoryImpl implements GooglePlaceResponseRepository {

	private final GooglePlaceResponseJpaRepository jpaRepository;

	@Override
	public GooglePlaceResponse save(GooglePlaceResponse googlePlaceResponse) {
		return jpaRepository.save(googlePlaceResponse);
	}

	@Override
	public Optional<GooglePlaceResponse> findByExhibitionPlaceId(Long exhibitionPlaceId) {
		if (exhibitionPlaceId == null) {
			return Optional.empty();
		}
		return jpaRepository.findByExhibitionPlaceId(exhibitionPlaceId);
	}
}
