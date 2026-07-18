package modi.backend.domain.exhibition.sync;

import modi.backend.domain.exhibition.sync.entity.ExternalApiCall;

/**
 * 외부 호출 감사 저장 포트(Spring 무의존). append-only라 저장만 제공한다.
 */
public interface ExternalApiCallRepository {

	ExternalApiCall save(ExternalApiCall externalApiCall);
}
