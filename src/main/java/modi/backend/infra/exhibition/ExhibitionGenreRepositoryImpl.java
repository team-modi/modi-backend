package modi.backend.infra.exhibition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.ExhibitionGenre;
import modi.backend.domain.exhibition.ExhibitionGenreRepository;

@Repository
@RequiredArgsConstructor
public class ExhibitionGenreRepositoryImpl implements ExhibitionGenreRepository {

	private final ExhibitionGenreJpaRepository jpaRepository;

	@Override
	public ExhibitionGenre save(ExhibitionGenre exhibitionGenre) {
		return jpaRepository.save(exhibitionGenre);
	}

	@Override
	public Optional<ExhibitionGenre> findByExhibitionId(Long exhibitionId) {
		return jpaRepository.findByExhibitionId(exhibitionId);
	}

	@Override
	public List<ExhibitionGenre> findAllByExhibitionIds(Collection<Long> exhibitionIds) {
		// 빈 컬렉션에 IN () 을 던지면 DB마다 동작이 갈린다 — 호출부가 방어하지 않아도 되게 여기서 막는다.
		if (exhibitionIds == null || exhibitionIds.isEmpty()) {
			return List.of();
		}
		return jpaRepository.findAllByExhibitionIdIn(exhibitionIds);
	}
}
