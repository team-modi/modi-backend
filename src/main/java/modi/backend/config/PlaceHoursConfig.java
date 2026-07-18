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

import modi.backend.domain.exhibition.sync.port.PlaceHoursProvider;
import modi.backend.infra.exhibition.sync.google.GoogleMapsApi;
import modi.backend.infra.exhibition.sync.google.GooglePlaceHoursProvider;
import modi.backend.infra.exhibition.sync.mock.MockPlaceHoursProvider;
import reactor.netty.http.client.HttpClient;

/**
 * 전시 영업시간(구글 Places New) 관련 빈 등록 — 장르(Gemini) 구성과 동형.
 * <p>
 * mock·google 두 조회기는 {@code @Component}로 항상 공존하고, 여기서 {@code app.exhibition.place-hours.provider}(+키 유무)에 따라
 * 주 조회기(@Primary)를 고른다. 주입 지점(enricher)은 선택된 하나만 본다(DIP). 기본은 mock이라 로컬·CI·develop은 유료호출 0.
 */
@Configuration
@EnableConfigurationProperties(PlaceHoursProperties.class)
public class PlaceHoursConfig {

	/** 구글 Places 전용 WebClient. baseUrl·응답 타임아웃(워커 스레드 장기 점유·부팅 지연 방지)을 설정에서 주입한다. */
	@Bean
	public WebClient googleMapsWebClient(PlaceHoursProperties properties) {
		HttpClient httpClient = HttpClient.create()
				.responseTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
		return WebClient.builder()
				.baseUrl(properties.baseUrl())
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.build();
	}

	/** {@link GoogleMapsApi} 선언형 클라이언트 — {@link #googleMapsWebClient} 위에 HTTP Interface 프록시를 세운다. */
	@Bean
	public GoogleMapsApi googleMapsApi(WebClient googleMapsWebClient) {
		return HttpServiceProxyFactory.builderFor(WebClientAdapter.create(googleMapsWebClient)).build()
				.createClient(GoogleMapsApi.class);
	}

	/**
	 * 주 영업시간 조회기 선택. {@code provider=google}이고 키가 있으면 실호출기, 그 외(기본·키없음)면 mock을 @Primary로 노출한다.
	 * 두 구현 빈은 그대로 존재하므로(공존), 설정만 바꿔 무중단 교체할 수 있다.
	 */
	@Bean
	@Primary
	public PlaceHoursProvider placeHoursProvider(PlaceHoursProperties properties,
			MockPlaceHoursProvider mockPlaceHoursProvider, GooglePlaceHoursProvider googlePlaceHoursProvider) {
		return properties.useGoogle() ? googlePlaceHoursProvider : mockPlaceHoursProvider;
	}
}
