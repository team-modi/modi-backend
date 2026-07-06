package modi.backend.application.record;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import modi.backend.config.AiProperties;
import modi.backend.domain.ai.AiErrorCode;
import modi.backend.support.error.CoreException;

/**
 * 사용자당 AI 호출 최소 간격을 강제하는 간단한 인메모리 쿨다운.
 * "다른 질문 보기"/"다시 다듬기" 반복 클릭으로 유료 LLM 호출이 폭주하는 것을 막는다.
 * (단일 인스턴스 메모리 기준 — 다중 인스턴스/영구 제한이 필요하면 Redis 등으로 승격.)
 */
@Component
public class AiRateLimiter {

	private final Map<Long, Long> lastCallAtMs = new ConcurrentHashMap<>();
	private final long minIntervalMs;

	public AiRateLimiter(AiProperties properties) {
		this.minIntervalMs = properties.rateLimitSeconds() * 1000L;
	}

	/** 직전 허용 호출로부터 최소 간격이 지나지 않았으면 {@link AiErrorCode#AI_RATE_LIMITED}(429). */
	public void check(Long userId) {
		if (minIntervalMs <= 0 || userId == null) {
			return;
		}
		long now = System.currentTimeMillis();
		Long previous = lastCallAtMs.get(userId);
		if (previous != null && now - previous < minIntervalMs) {
			throw new CoreException(AiErrorCode.AI_RATE_LIMITED);
		}
		lastCallAtMs.put(userId, now);
	}
}
