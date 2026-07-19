package modi.backend.infra.exhibition.catalog;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.catalog.Artist;
import modi.backend.domain.exhibition.catalog.ArtistRepository;

/** {@link ArtistRepository} 어댑터(DIP). 살아있는 행만 본다. */
@Repository
@RequiredArgsConstructor
public class ArtistRepositoryImpl implements ArtistRepository {

	private final ArtistJpaRepository jpaRepository;

	@Override
	public Artist save(Artist artist) {
		return jpaRepository.save(artist);
	}

	@Override
	public Optional<Artist> findByName(String normalizedName) {
		if (normalizedName == null) {
			return Optional.empty();
		}
		return jpaRepository.findByNameAndDeletedAtIsNull(normalizedName);
	}
}
