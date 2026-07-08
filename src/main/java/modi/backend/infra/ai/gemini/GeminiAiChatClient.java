package modi.backend.infra.ai.gemini;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import modi.backend.config.AiProperties;
import modi.backend.domain.ai.AiChatClient;
import modi.backend.domain.ai.AiErrorCode;
import modi.backend.support.error.CoreException;

/**
 * {@link AiChatClient}의 Gemini(Google Generative Language API) 어댑터.
 * 키리스 SDK 없이 WebClient로 REST {@code generateContent}를 호출한다. {@code app.ai.provider=gemini}일 때만 빈 등록.
 * api-key 미설정이면 클라이언트를 만들지 않고, 호출 시 {@link AiErrorCode#AI_DISABLED}를 던진다(AI만 비활성, 나머지 정상).
 * 구조화 출력({@code completeStructured})은 {@code responseMimeType=application/json + responseSchema}로 형태를 강제한 뒤
 * Jackson으로 스키마 타입에 역직렬화한다. 모델·max-tokens·타임아웃은 {@link AiProperties} 설정값을 사용한다.
 * Claude 어댑터와 동일 포트를 구현하므로 {@code app.ai.provider}만 바꾸면 상호 전환된다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini")
public class GeminiAiChatClient implements AiChatClient {

	private static final Logger log = LoggerFactory.getLogger(GeminiAiChatClient.class);
	private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

	private final AiProperties properties;
	private final MeterRegistry meterRegistry;
	private final ObjectMapper objectMapper;
	private final WebClient webClient; // api-key 미설정 시 null

	public GeminiAiChatClient(AiProperties properties, MeterRegistry meterRegistry, ObjectMapper objectMapper) {
		this.properties = properties;
		this.meterRegistry = meterRegistry;
		this.objectMapper = objectMapper;
		this.webClient = properties.isConfigured()
				? WebClient.builder()
						.baseUrl(BASE_URL)
						.defaultHeader("x-goog-api-key", properties.apiKey()) // 키를 URL이 아닌 헤더로(로그 노출 방지)
						.build()
				: null;
	}

	@Override
	public String complete(String systemPrompt, String userPrompt) {
		JsonNode response = call(requestBody(systemPrompt, userPrompt, null));
		return extractText(response).trim();
	}

	@Override
	public <T> T completeStructured(String systemPrompt, String userPrompt, Class<T> schemaType) {
		JsonNode response = call(requestBody(systemPrompt, userPrompt, GeminiSchema.of(schemaType)));
		String json = extractText(response).trim();
		try {
			return objectMapper.readValue(json, schemaType);
		} catch (Exception e) {
			throw new CoreException(AiErrorCode.AI_GENERATION_FAILED, "구조화 응답 파싱 실패: " + e.getMessage());
		}
	}

	private JsonNode call(Map<String, Object> body) {
		requireEnabled();
		try {
			JsonNode response = webClient.post()
					.uri("/models/{model}:generateContent", properties.model())
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(body)
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block(Duration.ofSeconds(properties.timeoutSeconds()));
			if (response == null) {
				throw new CoreException(AiErrorCode.AI_GENERATION_FAILED, "빈 응답");
			}
			record(response);
			return response;
		} catch (CoreException e) {
			throw e;
		} catch (Exception e) {
			throw new CoreException(AiErrorCode.AI_GENERATION_FAILED, e.getMessage());
		}
	}

	private Map<String, Object> requestBody(String systemPrompt, String userPrompt, Map<String, Object> responseSchema) {
		Map<String, Object> generationConfig = new LinkedHashMap<>();
		generationConfig.put("maxOutputTokens", properties.maxTokens());
		// gemini-2.5-flash는 thinking 모델 — thinking이 maxOutputTokens를 소진해 (특히 구조화 출력에서)
		//   응답 텍스트가 비거나 잘리는 것을 막는다. 짧은 결정형 작업(질문 3개·감상문)이라 thinking 불필요 → 0으로 비활성화.
		generationConfig.put("thinkingConfig", Map.of("thinkingBudget", 0));
		if (responseSchema != null) {
			generationConfig.put("responseMimeType", "application/json");
			generationConfig.put("responseSchema", responseSchema);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
		body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))));
		body.put("generationConfig", generationConfig);
		return body;
	}

	/** {@code candidates[0].content.parts[*].text}를 이어붙인다. 비면 생성 실패(finishReason을 로그에 남긴다). */
	private String extractText(JsonNode response) {
		JsonNode candidate = response.path("candidates").path(0);
		JsonNode parts = candidate.path("content").path("parts");
		if (!parts.isArray() || parts.isEmpty()) {
			String finishReason = candidate.path("finishReason").asString("UNKNOWN");
			throw new CoreException(AiErrorCode.AI_GENERATION_FAILED,
					"응답 본문이 비어 있습니다(finishReason=" + finishReason + ").");
		}
		StringBuilder sb = new StringBuilder();
		for (JsonNode part : parts) {
			String text = part.path("text").asString("");
			if (!text.isBlank()) {
				sb.append(text);
			}
		}
		String text = sb.toString();
		if (text.isBlank()) {
			throw new CoreException(AiErrorCode.AI_GENERATION_FAILED, "응답 텍스트가 비어 있습니다.");
		}
		return text;
	}

	private void requireEnabled() {
		if (webClient == null) {
			throw new CoreException(AiErrorCode.AI_DISABLED);
		}
	}

	/** {@code usageMetadata} 토큰 사용량 로깅 + Micrometer 카운터(비용/스파이크 모니터링). 실패는 무시(본 응답에 영향 없음). */
	private void record(JsonNode response) {
		try {
			JsonNode usage = response.path("usageMetadata");
			long input = usage.path("promptTokenCount").asLong(0);
			long output = usage.path("candidatesTokenCount").asLong(0);
			log.info("AI 호출 사용량 provider=gemini model={} input={} output={}", properties.model(), input, output);
			meterRegistry.counter("modi.ai.calls", "model", properties.model()).increment();
			meterRegistry.counter("modi.ai.tokens", "model", properties.model(), "type", "input").increment(input);
			meterRegistry.counter("modi.ai.tokens", "model", properties.model(), "type", "output").increment(output);
		} catch (Exception ignored) {
			// 모니터링은 부가 기능 — 실패해도 응답 처리엔 영향 없음
		}
	}
}
