package modi.backend.ingestion.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.ingestion.domain.entity.CultureDetailSnapshot;

public interface CultureDetailSnapshotJpaRepository extends JpaRepository<CultureDetailSnapshot, Long> {

	Optional<CultureDetailSnapshot> findByExternalId(String externalId);
}
