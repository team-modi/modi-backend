package modi.backend.infra.exhibition.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.sync.entity.ExternalApiCall;

public interface ExternalApiCallJpaRepository extends JpaRepository<ExternalApiCall, Long> {
}
