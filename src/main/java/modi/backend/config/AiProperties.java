package modi.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI(LLM) 설정. {@code app.ai.*} 바인딩. provider는 교체 가능하도록 문자열로 둔다(claude | gemini).
 * api-key는 시크릿 → 환경변수 주입. provider와 짝지어 지정한다(교차 오염 방지):
 *   claude=ANTHROPIC_API_KEY, gemini=AI_API_KEY(=Gemini 키). 미설정이면 어댑터가 비활성(AI_DISABLED)으로 동작.
 * model 미지정 시 provider에 맞는 기본 모델을 쓴다(claude → claude-opus-4-8, gemini → gemini-2.5-flash).
 * timeoutSeconds: 외부 LLM 호출 타임아웃(워커 스레드 장기 점유 방지).
 * rateLimitSeconds: 사용자당 AI 호출 최소 간격(반복 클릭에 의한 유료 호출 폭주 방지).
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String provider, String model, String apiKey, Long maxTokens,
		Long timeoutSeconds, Long rateLimitSeconds, Integer maxRetries, Long maxRetryDelaySeconds) {

	private static final String DEFAULT_CLAUDE_MODEL = "claude-opus-4-8";
	// 무료 한도가 flash보다 훨씬 큰 flash-lite를 기본값으로 — 감상문 질문/다듬기는 가벼운 작업이라 lite로 품질 충분.
	//   (장르 백필과 한도를 나눠 쓰려면 AI_MODEL로 flash 등 다른 모델을 지정하면 별도 한도 버킷을 사용.)
	private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash-lite";

	public AiProperties {
		if (provider == null || provider.isBlank()) {
			provider = "claude";
		}
		if (model == null || model.isBlank()) {
			model = "gemini".equalsIgnoreCase(provider) ? DEFAULT_GEMINI_MODEL : DEFAULT_CLAUDE_MODEL;
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
		if (maxRetries == null || maxRetries < 0) {
			maxRetries = 2;
		}
		if (maxRetryDelaySeconds == null || maxRetryDelaySeconds < 0) {
			maxRetryDelaySeconds = 4L;
		}
	}

	/** api-key가 실제로 채워졌는지(빈 문자열 = 미설정). */
	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}
}
