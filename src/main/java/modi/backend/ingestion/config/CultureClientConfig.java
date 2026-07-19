package modi.backend.ingestion.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import modi.backend.ingestion.infra.culture.CultureApi;
import modi.backend.ingestion.infra.culture.CultureExhibitionClient;

/**
 * 공공데이터 전시 API(한눈에보는문화정보) 클라이언트 배선 — 수집 전용이라 ingestion 슬라이스가 소유한다(ADR-12,
 * 코어 HttpClientConfig에는 OAuth 클라이언트만 남는다). RestClient 기반 HTTP Interface(ADR-09).
 */
@Configuration
@EnableConfigurationProperties(PublicDataProperties.class)
public class CultureClientConfig {

	/**
	 * 공공데이터 전시 API 전용 RestClient. baseUrl은 설정(app.public-data.culture.base-url)에서 주입한다.
	 * 응답이 XML이라 JSON 컨버터는 쓰지 않는다 — 문자열로 받아 {@link modi.backend.ingestion.infra.culture.CultureApiMapper}가 XmlMapper로 직접 파싱한다.
	 * <p>
	 * 읽기 타임아웃을 반드시 건다 — 상세 백필이 단일 스케줄러 스레드에서 이 클라이언트를 반복 호출하는데,
	 * 게이트웨이가 TCP만 받고 응답을 안 주면(무응답 stall) 타임아웃이 없을 경우 스레드와 DB 커넥션이 영구 점유돼
	 * 정기 동기화·보강이 전부 멈춘다(gemini 클라이언트와 동일한 방어).
	 */
	@Bean
	public RestClient cultureRestClient(PublicDataProperties properties) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
		requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
		String baseUrl = properties.baseUrl() == null ? "" : properties.baseUrl();
		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				// 원천이 Content-Type에 charset을 안 실어도 UTF-8로 읽는다 — String 컨버터 기본값(ISO-8859-1)이면 한글이 깨진다.
				.configureMessageConverters(builder -> builder
						.withStringConverter(new StringHttpMessageConverter(StandardCharsets.UTF_8)))
				.build();
	}

	/**
	 * {@link CultureExhibitionClient}가 사용하는 realm2/detail2 선언형 클라이언트.
	 * {@link #cultureRestClient} 위에 프록시를 세워 baseUrl·타임아웃 설정을 그대로 물려받는다.
	 */
	@Bean
	public CultureApi cultureApi(RestClient cultureRestClient) {
		return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(cultureRestClient)).build()
				.createClient(CultureApi.class);
	}
}
