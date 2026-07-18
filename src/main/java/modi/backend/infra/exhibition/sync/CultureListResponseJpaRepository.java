package modi.backend.infra.exhibition.sync;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.sync.entity.CultureListResponse;

public interface CultureListResponseJpaRepository extends JpaRepository<CultureListResponse, Long> {

	Optional<CultureListResponse> findByExternalId(String externalId);
}
