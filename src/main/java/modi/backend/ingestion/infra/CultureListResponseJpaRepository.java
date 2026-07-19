package modi.backend.ingestion.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.ingestion.domain.entity.CultureListResponse;

public interface CultureListResponseJpaRepository extends JpaRepository<CultureListResponse, Long> {

	Optional<CultureListResponse> findByExternalId(String externalId);
}
