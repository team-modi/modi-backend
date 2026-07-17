package modi.backend.infra.exhibition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.ExhibitionDetail;
import modi.backend.domain.exhibition.ExhibitionDetailRepository;

/** {@link ExhibitionDetailRepository} 어댑터(DIP). */
@Repository
@RequiredArgsConstructor
public class ExhibitionDetailRepositoryImpl implements ExhibitionDetailRepository {

	private final ExhibitionDetailJpaRepository jpaRepository;

	@Override
	public ExhibitionDetail save(ExhibitionDetail detail) {
		return jpaRepository.save(detail);
	}

	@Override
	public Optional<ExhibitionDetail> findByExhibitionId(Long exhibitionId) {
		return jpaRepository.findByExhibitionId(exhibitionId);
	}

	@Override
	public boolean existsByExhibitionId(Long exhibitionId) {
		return jpaRepository.existsByExhibitionId(exhibitionId);
	}

	@Override
	public List<ExhibitionDetail> findAllByExhibitionIds(Collection<Long> exhibitionIds) {
		if (exhibitionIds == null || exhibitionIds.isEmpty()) {
			return List.of();
		}
		return jpaRepository.findByExhibitionIdIn(exhibitionIds);
	}

	@Override
	public List<ExhibitionDetail> findAllWithDescription() {
		return jpaRepository.findByDescriptionIsNotNull();
	}
}
