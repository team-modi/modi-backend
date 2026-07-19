package modi.backend.ingestion.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.ingestion.domain.entity.ExternalApiCall;

public interface ExternalApiCallJpaRepository extends JpaRepository<ExternalApiCall, Long> {
}
