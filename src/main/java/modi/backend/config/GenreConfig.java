package modi.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import modi.backend.domain.exhibition.GenreClassifier;
import modi.backend.infra.genre.GeminiApi;
import modi.backend.infra.genre.GeminiGenreClassifier;
import modi.backend.infra.genre.RandomGenreClassifier;
import reactor.netty.http.client.HttpClient;

/**
 * 장르 분류 관련 빈 등록. Gemini 선언형 HTTP 클라이언트({@link GeminiApi})와, yml로 선택되는 주 분류기({@link GenreClassifier})를 조립한다.
 * <p>
 * 랜덤·Gemini 두 구현은 {@code @Component}로 항상 빈으로 <b>공존</b>하고, 여기서 {@code app.exhibition.genre.classifier}에 따라
 * 주 분류기(@Primary)를 고른다 — 주입 지점(Facade·초기화기)은 선택된 하나만 본다. Gemini 어댑터는 폴백용으로 랜덤 구현을 직접 참조한다.
 */
@Configuration
@EnableConfigurationProperties({ GeminiProperties.class, GenreProperties.class })
public class GenreConfig {

	/**
	 * Gemini 전용 WebClient. baseUrl·응답 타임아웃(워커 스레드 장기 점유·부팅 지연 방지)을 설정에서 주입한다.
	 * 응답의 여분 필드(role·thoughtSignature·usageMetadata 등)는 {@code GeminiDto} 응답 record의
	 * {@code @JsonIgnoreProperties(ignoreUnknown)}로 관대하게 파싱하므로 별도 코덱 커스터마이즈는 두지 않는다.
	 */
	@Bean
	public WebClient geminiWebClient(GeminiProperties properties) {
		HttpClient httpClient = HttpClient.create()
				.responseTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
		return WebClient.builder()
				.baseUrl(properties.baseUrl())
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.build();
	}

	/** {@link GeminiApi} 선언형 클라이언트 — {@link #geminiWebClient} 위에 HTTP Interface 프록시를 세운다. */
	@Bean
	public GeminiApi geminiApi(WebClient geminiWebClient) {
		return HttpServiceProxyFactory.builderFor(WebClientAdapter.create(geminiWebClient)).build()
				.createClient(GeminiApi.class);
	}

	/**
	 * 주 장르 분류기 선택. {@code app.exhibition.genre.classifier=gemini}면 AI, 그 외면 랜덤을 @Primary로 노출한다.
	 * 두 구현 빈은 그대로 존재하므로(공존), 설정만 바꿔 무중단 교체할 수 있다.
	 */
	@Bean
	@Primary
	public GenreClassifier genreClassifier(GenreProperties properties,
			RandomGenreClassifier randomGenreClassifier, GeminiGenreClassifier geminiGenreClassifier) {
		return properties.useGemini() ? geminiGenreClassifier : randomGenreClassifier;
	}
}
