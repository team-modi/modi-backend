package modi.backend.infra.exhibition;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.CultureListResponse;
import modi.backend.domain.exhibition.CultureListResponseRepository;

@Repository
@RequiredArgsConstructor
public class CultureListResponseRepositoryImpl implements CultureListResponseRepository {

	private final CultureListResponseJpaRepository jpaRepository;

	@Override
	public CultureListResponse save(CultureListResponse cultureListResponse) {
		return jpaRepository.save(cultureListResponse);
	}

	@Override
	public Optional<CultureListResponse> findByExternalId(String externalId) {
		return jpaRepository.findByExternalId(externalId);
	}
}
