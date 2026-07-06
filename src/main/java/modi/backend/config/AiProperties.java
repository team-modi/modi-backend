package modi.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI(LLM) 설정. {@code app.ai.*} 바인딩. provider는 교체 가능하도록 문자열로 둔다(우선 claude).
 * api-key는 시크릿 → 환경변수(ANTHROPIC_API_KEY) 주입. 미설정이면 어댑터가 비활성(AI_DISABLED)으로 동작.
 * timeoutSeconds: 외부 LLM 호출 타임아웃(워커 스레드 장기 점유 방지).
 * rateLimitSeconds: 사용자당 AI 호출 최소 간격(반복 클릭에 의한 유료 호출 폭주 방지).
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String provider, String model, String apiKey, Long maxTokens,
		Long timeoutSeconds, Long rateLimitSeconds) {

	public AiProperties {
		if (provider == null || provider.isBlank()) {
			provider = "claude";
		}
		if (model == null || model.isBlank()) {
			model = "claude-opus-4-8";
		}
		if (maxTokens == null || maxTokens <= 0) {
			maxTokens = 1024L;
		}
		if (timeoutSeconds == null || timeoutSeconds <= 0) {
			timeoutSeconds = 30L;
		}
		if (rateLimitSeconds == null || rateLimitSeconds < 0) {
			rateLimitSeconds = 3L;
		}
	}

	/** api-key가 실제로 채워졌는지(빈 문자열 = 미설정). */
	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}
}
