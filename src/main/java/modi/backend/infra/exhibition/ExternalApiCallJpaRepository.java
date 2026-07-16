package modi.backend.infra.exhibition;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.ExternalApiCall;

public interface ExternalApiCallJpaRepository extends JpaRepository<ExternalApiCall, Long> {
}
