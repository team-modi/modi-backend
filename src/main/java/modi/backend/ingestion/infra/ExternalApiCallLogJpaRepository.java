package modi.backend.ingestion.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.ingestion.domain.entity.ExternalApiCallLog;

public interface ExternalApiCallLogJpaRepository extends JpaRepository<ExternalApiCallLog, Long> {
}
