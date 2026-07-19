package modi.backend.ingestion.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.ingestion.domain.entity.IngestionRun;

public interface IngestionRunJpaRepository extends JpaRepository<IngestionRun, Long> {
}
