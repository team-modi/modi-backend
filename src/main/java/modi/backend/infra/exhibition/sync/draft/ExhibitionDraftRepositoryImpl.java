package modi.backend.infra.exhibition.sync.draft;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.sync.draft.DraftStatus;
import modi.backend.domain.exhibition.sync.draft.ExhibitionDraft;
import modi.backend.domain.exhibition.sync.draft.ExhibitionDraftRepository;

@Repository
@RequiredArgsConstructor
public class ExhibitionDraftRepositoryImpl implements ExhibitionDraftRepository {

	private final ExhibitionDraftJpaRepository jpaRepository;

	@Override
	public ExhibitionDraft save(ExhibitionDraft draft) {
		return jpaRepository.save(draft);
	}

	@Override
	public Optional<ExhibitionDraft> findByExternalId(String externalId) {
		return jpaRepository.findByExternalId(externalId);
	}

	@Override
	public long countByStatus(DraftStatus status) {
		return jpaRepository.countByStatus(status);
	}
}
