package modi.backend.infra.exhibition.sync.draft;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.sync.draft.DraftStatus;
import modi.backend.domain.exhibition.sync.draft.ExhibitionDraft;

public interface ExhibitionDraftJpaRepository extends JpaRepository<ExhibitionDraft, Long> {

	Optional<ExhibitionDraft> findByExternalId(String externalId);

	long countByStatus(DraftStatus status);
}
