package modi.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import modi.backend.infra.auth.GoogleApi;
import modi.backend.infra.auth.KakaoApi;

/**
 * WebClient 기반 HTTP Interface 클라이언트 등록. (선언형 REST 엔드포인트 관리)
 */
@Configuration
public class HttpClientConfig {

	@Bean
	public KakaoApi kakaoApi() {
		return createClient(KakaoApi.class);
	}

	@Bean
	public GoogleApi googleApi() {
		return createClient(GoogleApi.class);
	}

	private <T> T createClient(Class<T> apiType) {
		WebClient webClient = WebClient.builder().build();
		HttpServiceProxyFactory factory = HttpServiceProxyFactory
				.builderFor(WebClientAdapter.create(webClient))
				.build();
		return factory.createClient(apiType);
	}
}
