package modi.backend.infra.exhibition;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.CultureDetailResponse;
import modi.backend.domain.exhibition.CultureDetailResponseRepository;

@Repository
@RequiredArgsConstructor
public class CultureDetailResponseRepositoryImpl implements CultureDetailResponseRepository {

	private final CultureDetailResponseJpaRepository jpaRepository;

	@Override
	public CultureDetailResponse save(CultureDetailResponse cultureDetailResponse) {
		return jpaRepository.save(cultureDetailResponse);
	}

	@Override
	public Optional<CultureDetailResponse> findByExternalId(String externalId) {
		return jpaRepository.findByExternalId(externalId);
	}
}
