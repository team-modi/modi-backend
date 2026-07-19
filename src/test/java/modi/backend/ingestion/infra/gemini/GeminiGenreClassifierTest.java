package modi.backend.ingestion.infra.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import modi.backend.ingestion.config.GeminiProperties;
import modi.backend.domain.exhibition.genre.GenreProvider;
import modi.backend.ingestion.domain.data.GenreClassification;
import modi.backend.ingestion.domain.data.GenreResult;
import modi.backend.ingestion.domain.port.GenreClassificationException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * {@link GeminiGenreClassifier} 실HTTP 계약 검증(MockWebServer). 실제 Gemini 대신 목 서버로
 * 응답 포맷·구조화 요청·<b>실패 시 예외(ADR-11 계약 반전)</b>를 확인한다 — 폴백값·내부 재시도는 이제 없다
 * (즉시 재시도·2차 전환은 폴백 체인, durable 재시도는 아웃박스의 몫).
 */
class GeminiGenreClassifierTest {

	private MockWebServer server;
	private GeminiGenreClassifier classifier;

	private final GenreClassification input = new GenreClassification(
			"모네에서 세잔까지 — 인상주의 특별전", "PAINTING", "인상주의 대표작 특별전", "예술의전당 한가람미술관", null, "전시");

	@BeforeEach
	void setUp() throws IOException {
		server = new MockWebServer();
		server.start();
		classifier = classifierWith(new GeminiProperties(
				server.url("/").toString(), "test-api-key", "gemini-2.5-flash", 5L, 1, 0L));
	}

	@AfterEach
	void tearDown() throws IOException {
		server.shutdown();
	}

	private GeminiGenreClassifier classifierWith(GeminiProperties properties) {
		// 운영 조립(GenreConfig)과 동일하게 JDK 팩토리 고정 — 테스트 클래스패스의 Apache HttpClient5(Testcontainers 전이)가
		// 자동감지되면 429를 전송 계층에서 한 번 더 재시도해(DefaultHttpRequestRetryStrategy) 요청 수 검증이 깨진다.
		RestClient restClient = RestClient.builder().baseUrl(properties.baseUrl())
				.requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory())
				.build();
		GeminiApi api = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build()
				.createClient(GeminiApi.class);
		return new GeminiGenreClassifier(api, properties, new SimpleMeterRegistry());
	}

	@Test
	@DisplayName("200 응답의 enum 값을 그대로 장르로 반환하고, 구조화 요청을 올바른 경로/헤더로 보낸다")
	void classify_success_returnsGenreAndSendsStructuredRequest() throws InterruptedException {
		server.enqueue(candidateResponse("사진"));

		GenreResult result = classifier.classify(input);

		assertThat(result.genreKeyword()).isEqualTo("사진");
		RecordedRequest recorded = server.takeRequest();
		assertThat(recorded.getPath()).isEqualTo("/v1beta/models/gemini-2.5-flash:generateContent");
		assertThat(recorded.getHeader("x-goog-api-key")).isEqualTo("test-api-key");
		String body = recorded.getBody().readUtf8();
		assertThat(body).contains("text/x.enum").contains("\"enum\"").contains("회화·드로잉");
	}

	@Test
	@DisplayName("성공 시 계보로 provider=GEMINI와 응답 modelVersion(요청 모델이 아니라)을 붙인다")
	void classify_success_carriesProviderAndResponseModelVersion() {
		// 요청 모델은 "gemini-2.5-flash"(별칭일 수 있음)인데 실제 서빙 모델은 응답이 말한 값이다 — 계보엔 응답 쪽이 남아야 한다.
		server.enqueue(candidateResponse("사진", "gemini-2.5-flash-002"));

		GenreResult result = classifier.classify(input);

		assertThat(result.provider()).isEqualTo(GenreProvider.GEMINI);
		assertThat(result.model()).isEqualTo("gemini-2.5-flash-002");
	}

	@Test
	@DisplayName("응답이 마스터에 없는 값이면 폴백값 대신 분류 실패 예외를 던진다(ADR-11)")
	void classify_unknownGenre_throws() {
		server.enqueue(candidateResponse("K-POP 콘서트"));

		// 가짜 값이 저장되는 순간 미분류 대상에서 영구 이탈하던 과거 문제 — 이제 실패는 값이 아니라 예외다.
		assertThatThrownBy(() -> classifier.classify(input))
				.isInstanceOf(GenreClassificationException.class)
				.hasMessageContaining("마스터에 없음");
	}

	@Test
	@DisplayName("429는 내부 재시도 없이 단일 시도로 예외를 던진다(재시도·전환은 체인·아웃박스의 몫)")
	void classify_rateLimited_throwsWithoutInternalRetry() {
		server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":429}}"));

		assertThatThrownBy(() -> classifier.classify(input))
				.isInstanceOf(GenreClassificationException.class);
		assertThat(server.getRequestCount()).isEqualTo(1); // 수동 429 백오프 루프가 제거됐다 — 단일 시도.
	}

	@Test
	@DisplayName("api-key 미설정이면 외부 호출 없이 분류 실패 예외를 던진다(체인이 2차로 전환)")
	void classify_notConfigured_throwsWithoutCall() {
		GeminiGenreClassifier disabled = classifierWith(new GeminiProperties(
				server.url("/").toString(), "", "gemini-2.5-flash", 5L, 1, 0L));

		assertThatThrownBy(() -> disabled.classify(input))
				.isInstanceOf(GenreClassificationException.class);
		assertThat(server.getRequestCount()).isZero();
	}

	@Test
	@DisplayName("배치: 전시 여러 건을 단일 호출로 분류하고 JSON 배열 응답을 순서대로 매핑한다")
	void classifyAll_success_singleCall() throws InterruptedException {
		server.enqueue(arrayResponse("사진", "미디어아트"));
		List<GenreClassification> inputs = List.of(
				new GenreClassification("서울 사진전", null, "다큐멘터리 사진", "시립미술관", null, "전시"),
				new GenreClassification("미디어아트 페스타", null, "인터랙티브 영상 설치", "백남준아트센터", null, "전시"));

		List<GenreResult> genres = classifier.classifyAll(inputs);

		assertThat(genres).extracting(GenreResult::genreKeyword).containsExactly("사진", "미디어아트");
		// 배치 전 항목이 같은 응답에서 나왔으므로 계보도 동일하게 붙는다.
		assertThat(genres).allSatisfy(g -> {
			assertThat(g.provider()).isEqualTo(GenreProvider.GEMINI);
			assertThat(g.model()).isEqualTo("gemini-2.5-flash");
		});
		assertThat(server.getRequestCount()).isEqualTo(1); // 전시마다 호출하지 않고 단일 호출
		RecordedRequest recorded = server.takeRequest();
		String body = recorded.getBody().readUtf8();
		assertThat(body).contains("application/json").contains("\"type\":\"ARRAY\"").contains("[0]").contains("[1]");
	}

	@Test
	@DisplayName("배치: 응답 배열이 입력보다 짧으면 배치 전체를 실패로 본다(부분 폴백값 금지 — ADR-11)")
	void classifyAll_shortResponse_throws() {
		server.enqueue(arrayResponse("사진")); // 입력 2건인데 1건만 응답
		List<GenreClassification> inputs = List.of(
				new GenreClassification("서울 사진전", null, null, null, null, null),
				new GenreClassification("무제", null, null, null, null, null));

		assertThatThrownBy(() -> classifier.classifyAll(inputs))
				.isInstanceOf(GenreClassificationException.class)
				.hasMessageContaining("입력 크기와 다름");
	}

	@Test
	@DisplayName("배치: 429면 단일 시도로 예외를 던진다(아웃박스가 durable 재시도)")
	void classifyAll_rateLimited_throws() {
		server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":429}}"));
		List<GenreClassification> inputs = List.of(
				new GenreClassification("A", null, null, null, null, null),
				new GenreClassification("B", null, null, null, null, null));

		assertThatThrownBy(() -> classifier.classifyAll(inputs))
				.isInstanceOf(GenreClassificationException.class);
		assertThat(server.getRequestCount()).isEqualTo(1);
	}

	/**
	 * JSON 배열(장르 enum 배열) 200 응답. 실제 Gemini처럼 text 필드 값이 JSON 문자열이므로 내부 따옴표를 이스케이프한다
	 * (예: text = {@code [\"사진\", \"미디어아트\"]}).
	 */
	private static MockResponse arrayResponse(String... genres) {
		String arr = java.util.Arrays.stream(genres)
				.map(g -> "\\\"" + g + "\\\"").collect(java.util.stream.Collectors.joining(", "));
		String json = """
				{
				  "candidates": [ { "content": { "role": "model", "parts": [ { "text": "[%s]" } ] } } ],
				  "modelVersion": "gemini-2.5-flash"
				}
				""".formatted(arr);
		return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(json);
	}

	/** 실제 Gemini 응답 형태를 모사한 200 응답(여분 필드 role·finishReason 포함 — 관대한 파싱 검증). */
	private static MockResponse candidateResponse(String genreText) {
		return candidateResponse(genreText, "gemini-2.5-flash");
	}

	/** 응답 modelVersion을 지정하는 변형 — "요청 모델이 아니라 응답 모델을 계보에 남긴다"를 검증하기 위함. */
	private static MockResponse candidateResponse(String genreText, String modelVersion) {
		String json = """
				{
				  "candidates": [
				    {
				      "content": { "role": "model", "parts": [ { "text": "%s" } ] },
				      "finishReason": "STOP",
				      "index": 0
				    }
				  ],
				  "modelVersion": "%s"
				}
				""".formatted(genreText, modelVersion);
		return new MockResponse()
				.setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(json);
	}
}
