package modi.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI(LLM) 설정. {@code app.ai.*} 바인딩. provider는 교체 가능하도록 문자열로 둔다(우선 claude).
 * api-key는 시크릿 → 환경변수(ANTHROPIC_API_KEY) 주입. 미설정이면 어댑터가 비활성(AI_DISABLED)으로 동작.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String provider, String model, String apiKey, Long maxTokens) {

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
	}

	/** api-key가 실제로 채워졌는지(빈 문자열 = 미설정). */
	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}
}
