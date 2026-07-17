package modi.backend.infra.genre;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import modi.backend.config.GeminiProperties;
import modi.backend.domain.exhibition.GenreClassification;
import modi.backend.domain.exhibition.GenreKeyword;
import modi.backend.domain.exhibition.GenreProvider;
import modi.backend.domain.exhibition.GenreResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * {@link GeminiGenreClassifier} 실HTTP 계약·429 폴백 검증(MockWebServer). 실제 Gemini 대신 목 서버로
 * 응답 포맷·구조화 요청·오류 폴백을 확인한다. maxRetryDelaySeconds=0으로 두어 재시도 백오프가 테스트를 지연시키지 않게 한다.
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
		WebClient webClient = WebClient.builder().baseUrl(properties.baseUrl()).build();
		GeminiApi api = HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient)).build()
				.createClient(GeminiApi.class);
		return new GeminiGenreClassifier(api, properties, new RandomGenreClassifier(), new SimpleMeterRegistry());
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
	@DisplayName("응답이 마스터에 없는 값이면 랜덤으로 폴백하고, 그 사실을 provider=RANDOM으로 드러낸다")
	void classify_unknownGenre_fallsBackToRandom() {
		server.enqueue(candidateResponse("K-POP 콘서트"));

		GenreResult result = classifier.classify(input);

		assertThat(GenreKeyword.all()).contains(result.genreKeyword());
		// 폴백을 GEMINI로 기록하면 랜덤값이 AI 분류값으로 위장돼 영구 이탈한다.
		assertThat(result.provider()).isEqualTo(GenreProvider.RANDOM);
	}

	@Test
	@DisplayName("무료 한도 초과(429)가 재시도까지 지속되면 랜덤으로 폴백한다")
	void classify_rateLimited_fallsBackToRandom() {
		// maxRetries=1 → 총 2회 시도, 둘 다 429면 폴백.
		server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":429}}"));
		server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":429}}"));

		GenreResult result = classifier.classify(input);

		assertThat(GenreKeyword.all()).contains(result.genreKeyword());
		assertThat(result.provider()).isEqualTo(GenreProvider.RANDOM);
		assertThat(server.getRequestCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("429 후 재시도가 성공하면 그 값을 반환한다")
	void classify_rateLimitedThenSuccess_returnsGenre() {
		server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":429}}"));
		server.enqueue(candidateResponse("미디어아트"));

		GenreResult result = classifier.classify(input);

		assertThat(result.genreKeyword()).isEqualTo("미디어아트");
		assertThat(result.provider()).isEqualTo(GenreProvider.GEMINI);
	}

	@Test
	@DisplayName("api-key 미설정이면 외부 호출 없이 랜덤으로 폴백한다")
	void classify_notConfigured_fallsBackWithoutCall() {
		GeminiGenreClassifier disabled = classifierWith(new GeminiProperties(
				server.url("/").toString(), "", "gemini-2.5-flash", 5L, 1, 0L));

		GenreResult result = disabled.classify(input);

		assertThat(GenreKeyword.all()).contains(result.genreKeyword());
		assertThat(result.provider()).isEqualTo(GenreProvider.RANDOM);
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
	@DisplayName("배치: 응답 배열이 입력보다 짧으면 누락 항목만 랜덤으로 보정한다")
	void classifyAll_shortResponse_perItemFallback() {
		server.enqueue(arrayResponse("사진")); // 입력 2건인데 1건만 응답
		List<GenreClassification> inputs = List.of(
				new GenreClassification("서울 사진전", null, null, null, null, null),
				new GenreClassification("무제", null, null, null, null, null));

		List<GenreResult> genres = classifier.classifyAll(inputs);

		assertThat(genres).hasSize(2);
		assertThat(genres.get(0).genreKeyword()).isEqualTo("사진");
		assertThat(genres.get(0).provider()).isEqualTo(GenreProvider.GEMINI);
		// 누락 항목만 랜덤 보정 — 계보가 항목 단위라 성공분과 폴백분이 섞인 배치도 나중에 구분된다.
		assertThat(GenreKeyword.all()).contains(genres.get(1).genreKeyword());
		assertThat(genres.get(1).provider()).isEqualTo(GenreProvider.RANDOM);
	}

	@Test
	@DisplayName("배치: 429가 재시도까지 지속되면 전체를 랜덤으로 폴백한다")
	void classifyAll_rateLimited_fallsBackAll() {
		server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":429}}"));
		server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":429}}"));
		List<GenreClassification> inputs = List.of(
				new GenreClassification("A", null, null, null, null, null),
				new GenreClassification("B", null, null, null, null, null));

		List<GenreResult> genres = classifier.classifyAll(inputs);

		assertThat(genres).hasSize(2);
		assertThat(GenreKeyword.all()).containsAll(genres.stream().map(GenreResult::genreKeyword).toList());
		assertThat(genres).allSatisfy(g -> assertThat(g.provider()).isEqualTo(GenreProvider.RANDOM));
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
