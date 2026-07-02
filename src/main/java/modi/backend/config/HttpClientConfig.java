package modi.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import modi.backend.config.PublicDataProperties;
import modi.backend.infra.auth.GoogleApi;
import modi.backend.infra.auth.KakaoApi;
import modi.backend.infra.exhibition.CultureApi;
import modi.backend.infra.exhibition.CultureExhibitionClient;

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
	 */
	@Bean
	public WebClient cultureWebClient(PublicDataProperties properties) {
		ExchangeStrategies strategies = ExchangeStrategies.builder()
				.codecs(config -> config.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
				.build();
		String baseUrl = properties.baseUrl() == null ? "" : properties.baseUrl();
		return WebClient.builder()
				.baseUrl(baseUrl)
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
