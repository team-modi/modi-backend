package modi.backend.ingestion.infra.draft;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.ingestion.domain.draft.DraftStatus;
import modi.backend.ingestion.domain.draft.ExhibitionDraft;

public interface ExhibitionDraftJpaRepository extends JpaRepository<ExhibitionDraft, Long> {

	Optional<ExhibitionDraft> findByExternalId(String externalId);

	long countByStatus(DraftStatus status);
}
