package modi.backend.domain.exhibition;

import java.util.Optional;

/**
 * 문화포털 목록 응답 원본 저장소 포트(Spring 무의존).
 * 포트 메서드명에 저장 방식·필터 세부를 드러내지 않는다 — 그건 어댑터(Impl)의 몫이다.
 */
public interface CultureListResponseRepository {

	CultureListResponse save(CultureListResponse cultureListResponse);

	Optional<CultureListResponse> findByExternalId(String externalId);
}
