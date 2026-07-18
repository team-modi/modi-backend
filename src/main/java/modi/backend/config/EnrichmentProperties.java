package modi.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import modi.backend.domain.exhibition.enrichment.RetryPolicy;

/**
 * 통합 보강 작업큐({@code enrichment_job}) 설정. {@code app.exhibition.enrichment.*} 바인딩.
 *
 * <p>재시도 백오프({@link #retryPolicy()})와 선별 배치 크기·폴링 주기, 그리고 이벤트 구동 영업시간 재검증의
 * 최소 간격(설계 §4-1 가드)을 담는다.
 *
 * @param maxAttempts               재시도 가능한 실패를 몇 번까지 다시 시도할지(초과 시 FAILED_PERMANENT로 승격).
 * @param baseBackoffSeconds        지수 백오프의 기준 간격(첫 재시도까지의 초).
 * @param maxBackoffSeconds         백오프 상한(간격이 무한정 벌어지지 않게).
 * @param jobBatchSize              한 번의 드레인에서 집는 작업 수 상한(폴링 폭주 방지).
 * @param jobPollIntervalMs         작업 드레인 스케줄 주기(ms).
 * @param hoursRefreshMinIntervalDays 영업시간 재검증 최소 간격(일). 이 안에 조회된 장소는 HOURS_REFRESH enqueue를 건너뛴다.
 */
@ConfigurationProperties(prefix = "app.exhibition.enrichment")
public record EnrichmentProperties(Integer maxAttempts, Long baseBackoffSeconds, Long maxBackoffSeconds,
		Integer jobBatchSize, Long jobPollIntervalMs, Integer hoursRefreshMinIntervalDays) {

	public EnrichmentProperties {
		if (maxAttempts == null || maxAttempts < 1) {
			maxAttempts = 5;
		}
		if (baseBackoffSeconds == null || baseBackoffSeconds < 1) {
			baseBackoffSeconds = 60L;
		}
		if (maxBackoffSeconds == null || maxBackoffSeconds < baseBackoffSeconds) {
			maxBackoffSeconds = 3600L;
		}
		if (jobBatchSize == null || jobBatchSize < 1) {
			jobBatchSize = 50;
		}
		if (jobPollIntervalMs == null || jobPollIntervalMs < 1000) {
			jobPollIntervalMs = 60000L;
		}
		if (hoursRefreshMinIntervalDays == null || hoursRefreshMinIntervalDays < 0) {
			hoursRefreshMinIntervalDays = 30;
		}
	}

	/** 설정값으로 도메인 백오프 정책을 만든다(도메인은 설정을 모르므로 여기서 조립해 넘긴다). */
	public RetryPolicy retryPolicy() {
		return new RetryPolicy(maxAttempts, baseBackoffSeconds, maxBackoffSeconds);
	}
}
