package modi.backend.domain.exhibition.sync.outbox;

import java.time.LocalDateTime;

/**
 * 재시도 백오프 정책(값 객체) — {@link OutboxMessage}이 실패를 기록할 때 "다음 시도를 언제로, 몇 번까지"를 계산한다.
 *
 * <p>지수 백오프: {@code delay = min(baseBackoffSeconds × 2^(attemptCount-1), maxBackoffSeconds)}.
 * 실패가 거듭될수록 간격을 벌려(무료 AI RPM·외부 API를 지치게 하지 않게) 재시도하고, {@link #maxAttempts}를 넘기면
 * 영구 실패로 승격한다(무한 재시도 방지 — 현행이 못 하던 것). 설정값은 {@code OutboxProperties}에서 온다.
 *
 * <p>도메인 값이라 Spring/설정에 의존하지 않는다 — 순수 계산만 한다(단위 테스트가 컨텍스트 없이 검증한다).
 */
public record RetryPolicy(int maxAttempts, long baseBackoffSeconds, long maxBackoffSeconds) {

	public RetryPolicy {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts는 1 이상이어야 한다: " + maxAttempts);
		}
		if (baseBackoffSeconds < 1) {
			throw new IllegalArgumentException("baseBackoffSeconds는 1 이상이어야 한다: " + baseBackoffSeconds);
		}
		if (maxBackoffSeconds < baseBackoffSeconds) {
			throw new IllegalArgumentException("maxBackoffSeconds는 baseBackoffSeconds 이상이어야 한다");
		}
	}

	/** 시도 횟수를 넘겼는가(초과 시 RETRYABLE도 영구 실패로 승격). */
	public boolean isExhausted(int attemptCount) {
		return attemptCount >= maxAttempts;
	}

	/**
	 * {@code attemptCount}번째 실패 시점의 다음 재시도 도래 시각. 지수 백오프를 상한으로 캡한다.
	 * {@code 2^n} 오버플로를 피하려 시프트를 안전 범위(≤ 62)로 제한한 뒤 캡을 씌운다.
	 */
	public LocalDateTime nextAttemptAt(int attemptCount, LocalDateTime now) {
		int shift = Math.min(Math.max(attemptCount - 1, 0), 62);
		long factor = 1L << shift;
		long delay = (factor > maxBackoffSeconds / baseBackoffSeconds)
				? maxBackoffSeconds
				: Math.min(baseBackoffSeconds * factor, maxBackoffSeconds);
		return now.plusSeconds(delay);
	}
}
