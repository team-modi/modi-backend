package modi.backend.ingestion.infra.draft;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.ingestion.domain.draft.DraftStatus;
import modi.backend.ingestion.domain.draft.ExhibitionDraft;
import modi.backend.ingestion.domain.draft.ExhibitionDraftRepository;

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
