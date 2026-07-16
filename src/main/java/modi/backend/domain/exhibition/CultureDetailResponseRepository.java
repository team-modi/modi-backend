package modi.backend.domain.exhibition;

import java.util.Optional;

/**
 * 문화포털 상세 응답 원본 저장소 포트(Spring 무의존).
 */
public interface CultureDetailResponseRepository {

	CultureDetailResponse save(CultureDetailResponse cultureDetailResponse);

	Optional<CultureDetailResponse> findByExternalId(String externalId);
}
