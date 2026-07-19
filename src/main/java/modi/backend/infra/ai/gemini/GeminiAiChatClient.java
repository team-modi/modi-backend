package modi.backend.infra.ai.gemini;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import modi.backend.config.AiProperties;
import modi.backend.domain.ai.AiChatClient;
import modi.backend.domain.ai.AiErrorCode;
import modi.backend.support.error.CoreException;

/**
 * {@link AiChatClient}의 Gemini(Google Generative Language API) 어댑터.
 * 키리스 SDK 없이 RestClient로 REST {@code generateContent}를 호출한다. {@code app.ai.provider=gemini}일 때만 빈 등록.
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
	private final RestClient restClient; // api-key 미설정 시 null

	// 생성자가 둘(운영·테스트)이라 Spring이 주입 생성자를 못 고른다 → 운영용 public을 @Autowired로 명시.
	@Autowired
	public GeminiAiChatClient(AiProperties properties, MeterRegistry meterRegistry, ObjectMapper objectMapper) {
		this(properties, meterRegistry, objectMapper, BASE_URL);
	}

	// 테스트가 목 서버 URL로 baseUrl을 주입하기 위한 패키지 전용 생성자(운영은 위 public이 BASE_URL로 위임).
	GeminiAiChatClient(AiProperties properties, MeterRegistry meterRegistry, ObjectMapper objectMapper, String baseUrl) {
		this.properties = properties;
		this.meterRegistry = meterRegistry;
		this.objectMapper = objectMapper;
		if (properties.isConfigured()) {
			// 응답 상한은 요청 팩토리 읽기 타임아웃으로 건다(과거 block(Duration)의 대체 — 커넥션 계층에서 끊겨 더 정확).
			JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
			requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
			this.restClient = RestClient.builder()
					.baseUrl(baseUrl)
					.defaultHeader("x-goog-api-key", properties.apiKey()) // 키를 URL이 아닌 헤더로(로그 노출 방지)
					.requestFactory(requestFactory)
					.build();
		} else {
			this.restClient = null;
		}
	}

	@Override
	public String complete(String systemPrompt, String userPrompt) {
		return callWithRetry(() -> extractText(callOnce(requestBody(systemPrompt, userPrompt, null))).trim());
	}

	@Override
	public <T> T completeStructured(String systemPrompt, String userPrompt, Class<T> schemaType) {
		return callWithRetry(() -> {
			String json = extractText(callOnce(requestBody(systemPrompt, userPrompt, GeminiSchema.of(schemaType)))).trim();
			try {
				return objectMapper.readValue(json, schemaType);
			} catch (Exception e) {
				throw new CoreException(AiErrorCode.AI_GENERATION_FAILED, "구조화 응답 파싱 실패: " + e.getMessage());
			}
		});
	}

	/** generateContent 단일 호출(재시도 없음). 성공 시 사용량을 기록하고 원시 응답을 반환한다. */
	private JsonNode callOnce(Map<String, Object> body) {
		JsonNode response = restClient.post()
				.uri("/models/{model}:generateContent", properties.model())
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(JsonNode.class);
		if (response == null) {
			throw new CoreException(AiErrorCode.AI_GENERATION_FAILED, "빈 응답");
		}
		record(response);
		return response;
	}

	/**
	 * 한 번의 시도(HTTP 호출 + 텍스트 추출/파싱) 전체를 감싸 순간적 Gemini 장애를 서버측에서 재시도한다.
	 * 재시도 대상: 429(무료 한도 — Retry-After 백오프), 5xx(모델 과부하 등 — 고정 백오프),
	 *   빈 응답·구조화 파싱 실패(AI_GENERATION_FAILED — 고정 백오프). 관측된 실패는 30s 타임아웃이 아닌 빠른 5xx·빈 응답이라 이걸 노린다.
	 * 재시도 안 함: 타임아웃·네트워크 예외(재시도하면 타임아웃 지연만 배가), AI_DISABLED·AI_RATE_LIMITED.
	 * 429가 재시도까지 소진되면 AI_RATE_LIMITED(429)로 반환해 "잠시 후 다시 시도" UX를 준다(생성 실패와 구분).
	 */
	private <T> T callWithRetry(Supplier<T> attempt) {
		requireEnabled();
		int attempts = properties.maxRetries() + 1;
		for (int i = 0; i < attempts; i++) {
			try {
				return attempt.get();
			} catch (HttpClientErrorException.TooManyRequests e) {
				log.warn("AI 호출 429(무료 한도) 시도 {}/{}", i + 1, attempts);
				if (i < attempts - 1) {
					backoff(e.getResponseHeaders());
					continue;
				}
				throw new CoreException(AiErrorCode.AI_RATE_LIMITED, "AI 무료 한도 초과(429)");
			} catch (RestClientResponseException e) {
				if (e.getStatusCode().is5xxServerError() && i < attempts - 1) {
					log.warn("AI 호출 5xx({}) 재시도 {}/{}", e.getStatusCode().value(), i + 1, attempts);
					fixedBackoff(i);
					continue;
				}
				throw new CoreException(AiErrorCode.AI_GENERATION_FAILED,
						e.getStatusCode().value() + " " + e.getMessage());
			} catch (CoreException e) {
				if (isRetryable(e) && i < attempts - 1) {
					log.warn("AI 생성 실패(빈 응답/파싱) 재시도 {}/{}: {}", i + 1, attempts, e.getMessage());
					fixedBackoff(i);
					continue;
				}
				throw e;
			} catch (Exception e) {
				// 타임아웃·네트워크 오류 — 재시도하면 지연만 배가되므로 즉시 실패로 처리한다.
				throw new CoreException(AiErrorCode.AI_GENERATION_FAILED, e.getMessage());
			}
		}
		// 도달 불가(위 루프가 반환/예외로 끝남) — 방어적 안전망.
		throw new CoreException(AiErrorCode.AI_GENERATION_FAILED, "AI 호출 실패");
	}

	/** 생성 실패(빈 응답·구조화 파싱 실패)만 재시도 대상. AI_DISABLED·AI_RATE_LIMITED는 재시도하지 않는다. */
	private static boolean isRetryable(CoreException e) {
		return e.errorCode() == AiErrorCode.AI_GENERATION_FAILED;
	}

	/** 5xx·빈 응답 재시도용 고정 백오프 — 소량(시도마다 300ms씩 증가), {@code max-retry-delay-seconds} 상한 내에서 대기. */
	private void fixedBackoff(int i) {
		long wait = Math.min((i + 1) * 300L, properties.maxRetryDelaySeconds() * 1000L);
		if (wait <= 0) {
			return;
		}
		try {
			Thread.sleep(wait);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	/** 429 백오프 — Retry-After(초)만큼, 단 {@code max-retry-delay-seconds} 상한 내에서 대기한다. */
	private void backoff(HttpHeaders headers) {
		long wait = Math.min(retryAfterSeconds(headers), properties.maxRetryDelaySeconds());
		if (wait <= 0) {
			return;
		}
		try {
			Thread.sleep(wait * 1000L);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	/** Retry-After 헤더(초). 없거나 파싱 불가면 1초를 기본 백오프로 본다. */
	private static long retryAfterSeconds(HttpHeaders headers) {
		String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
		if (value != null) {
			try {
				return Math.max(0, Long.parseLong(value.trim()));
			} catch (NumberFormatException ignored) {
				// 날짜 형식 등은 무시하고 기본 백오프
			}
		}
		return 1;
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
		if (restClient == null) {
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
