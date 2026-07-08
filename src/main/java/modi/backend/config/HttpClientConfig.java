package modi.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import modi.backend.config.PublicDataProperties;
import modi.backend.infra.auth.GoogleApi;
import modi.backend.infra.auth.KakaoApi;
import modi.backend.infra.exhibition.CultureApi;
import modi.backend.infra.exhibition.CultureExhibitionClient;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient 기반 HTTP Interface 클라이언트 등록. (선언형 REST 엔드포인트 관리)
 */
@Configuration
@EnableConfigurationProperties(PublicDataProperties.class)
public class HttpClientConfig {

	@Bean
	public KakaoApi kakaoApi() {
		return createClient(KakaoApi.class);
	}

	@Bean
	public GoogleApi googleApi() {
		return createClient(GoogleApi.class);
	}

	/**
	 * 공공데이터 전시 API(한눈에보는문화정보) 전용 WebClient. baseUrl은 설정(app.public-data.culture.base-url)에서 주입한다.
	 * 응답이 XML이라 JSON 디코더는 쓰지 않는다 — 문자열로 받아 {@link modi.backend.infra.exhibition.CultureApiMapper}가 XmlMapper로 직접 파싱한다.
	 * (응답 크기가 페이지당 수십~수백 KB일 수 있어 인메모리 버퍼만 넉넉히 확보한다.)
	 * <p>
	 * 응답 타임아웃을 반드시 건다 — 상세 백필이 단일 스케줄러 스레드에서 이 클라이언트를 반복 호출하는데,
	 * 게이트웨이가 TCP만 받고 응답을 안 주면(무응답 stall) 타임아웃이 없을 경우 스레드와 DB 커넥션이 영구 점유돼
	 * 정기 동기화·보강이 전부 멈춘다(gemini 클라이언트와 동일한 방어).
	 */
	@Bean
	public WebClient cultureWebClient(PublicDataProperties properties) {
		ExchangeStrategies strategies = ExchangeStrategies.builder()
				.codecs(config -> config.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
				.build();
		HttpClient httpClient = HttpClient.create()
				.responseTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
		String baseUrl = properties.baseUrl() == null ? "" : properties.baseUrl();
		return WebClient.builder()
				.baseUrl(baseUrl)
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.exchangeStrategies(strategies)
				.build();
	}

	/**
	 * {@link CultureExhibitionClient}가 사용하는 realm2/detail2 선언형 클라이언트.
	 * {@link #cultureWebClient} 위에 프록시를 세워 baseUrl·8MB 인메모리 버퍼 설정을 그대로 물려받는다.
	 */
	@Bean
	public CultureApi cultureApi(WebClient cultureWebClient) {
		return HttpServiceProxyFactory.builderFor(WebClientAdapter.create(cultureWebClient)).build()
				.createClient(CultureApi.class);
	}

	private <T> T createClient(Class<T> apiType) {
		WebClient webClient = WebClient.builder().build();
		HttpServiceProxyFactory factory = HttpServiceProxyFactory
				.builderFor(WebClientAdapter.create(webClient))
				.build();
		return factory.createClient(apiType);
	}
}
