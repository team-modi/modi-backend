package modi.backend.ingestion.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.ingestion.domain.entity.CultureListSnapshot;

public interface CultureListSnapshotJpaRepository extends JpaRepository<CultureListSnapshot, Long> {

	Optional<CultureListSnapshot> findByExternalId(String externalId);
}
