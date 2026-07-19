package modi.backend.ingestion.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.ingestion.domain.entity.CultureDetailResponse;

public interface CultureDetailResponseJpaRepository extends JpaRepository<CultureDetailResponse, Long> {

	Optional<CultureDetailResponse> findByExternalId(String externalId);
}
