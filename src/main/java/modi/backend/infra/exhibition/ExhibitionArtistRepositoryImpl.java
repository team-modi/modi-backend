package modi.backend.infra.exhibition;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.ExhibitionArtist;
import modi.backend.domain.exhibition.ExhibitionArtistRepository;

/** {@link ExhibitionArtistRepository} 어댑터(DIP). */
@Repository
@RequiredArgsConstructor
public class ExhibitionArtistRepositoryImpl implements ExhibitionArtistRepository {

	private final ExhibitionArtistJpaRepository jpaRepository;

	@Override
	public ExhibitionArtist save(ExhibitionArtist exhibitionArtist) {
		return jpaRepository.save(exhibitionArtist);
	}

	@Override
	public boolean existsByExhibitionIdAndArtistId(Long exhibitionId, Long artistId) {
		return jpaRepository.existsByExhibitionIdAndArtistId(exhibitionId, artistId);
	}

	@Override
	public List<String> findArtistNames(Long exhibitionId) {
		if (exhibitionId == null) {
			return List.of();
		}
		return jpaRepository.findArtistNames(exhibitionId);
	}
}
