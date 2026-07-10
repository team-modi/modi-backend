package modi.backend.infra.ai.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import modi.backend.config.AiProperties;
import modi.backend.domain.ai.AiErrorCode;
import modi.backend.support.error.CoreException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * {@link GeminiAiChatClient} 재시도 계약 검증(MockWebServer). 순간적 Gemini 장애(5xx·빈 응답·429)가
 * 서버측 재시도로 복구되는지 확인한다. maxRetryDelaySeconds=0으로 두어 백오프가 테스트를 지연시키지 않게 한다.
 */
class GeminiAiChatClientTest {

	private MockWebServer server;
	private GeminiAiChatClient client;

	@BeforeEach
	void setUp() throws IOException {
		server = new MockWebServer();
		server.start();
		// maxRetries=2 → 총 3회 시도, maxRetryDelaySeconds=0 → 백오프 없음(빠른 테스트)
		AiProperties properties = new AiProperties(
				"gemini", "gemini-2.5-flash-lite", "test-api-key", 1024L, 5L, 0L, 2, 0L);
		client = new GeminiAiChatClient(
				properties, new SimpleMeterRegistry(), JsonMapper.builder().build(), server.url("/").toString());
	}

	@AfterEach
	void tearDown() throws IOException {
		server.shutdown();
	}

	@Test
	@DisplayName("5xx(503) 후 재시도가 성공하면 텍스트를 반환한다")
	void complete_5xxThenSuccess_returnsText() {
		server.enqueue(new MockResponse().setResponseCode(503).setBody("{\"error\":{\"code\":503}}"));
		server.enqueue(validResponse("복구된 응답"));

		String text = client.complete("시스템", "사용자");

		assertThat(text).isEqualTo("복구된 응답");
		assertThat(server.getRequestCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("빈 응답(candidates 본문 없음) 후 재시도가 성공하면 텍스트를 반환한다")
	void complete_emptyContentThenSuccess_returnsText() {
		server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
				.setBody("{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"MAX_TOKENS\"}]}"));
		server.enqueue(validResponse("두 번째 성공"));

		String text = client.complete("시스템", "사용자");

		assertThat(text).isEqualTo("두 번째 성공");
		assertThat(server.getRequestCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("모든 시도가 5xx면 AI_GENERATION_FAILED로 실패한다")
	void complete_5xxAllAttempts_throwsGenerationFailed() {
		for (int i = 0; i < 3; i++) {
			server.enqueue(new MockResponse().setResponseCode(503).setBody("{\"error\":{\"code\":503}}"));
		}

		assertThatThrownBy(() -> client.complete("시스템", "사용자"))
				.isInstanceOf(CoreException.class)
				.extracting(e -> ((CoreException) e).errorCode())
				.isEqualTo(AiErrorCode.AI_GENERATION_FAILED);
		assertThat(server.getRequestCount()).isEqualTo(3);
	}

	@Test
	@DisplayName("429 후 재시도가 성공하면 텍스트를 반환한다(기존 429 재시도 유지)")
	void complete_429ThenSuccess_returnsText() {
		server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "0")
				.setBody("{\"error\":{\"code\":429}}"));
		server.enqueue(validResponse("한도 회복"));

		String text = client.complete("시스템", "사용자");

		assertThat(text).isEqualTo("한도 회복");
		assertThat(server.getRequestCount()).isEqualTo(2);
	}

	/** 정상 200 응답 — candidates[0].content.parts[0].text에 본문을 담는다. */
	private static MockResponse validResponse(String text) {
		String json = """
				{
				  "candidates": [ { "content": { "role": "model", "parts": [ { "text": "%s" } ] }, "finishReason": "STOP" } ],
				  "usageMetadata": { "promptTokenCount": 10, "candidatesTokenCount": 5 }
				}
				""".formatted(text);
		return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(json);
	}
}
