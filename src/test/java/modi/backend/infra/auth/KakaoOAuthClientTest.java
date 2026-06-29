package modi.backend.infra.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.config.OAuthProperties;

class KakaoOAuthClientTest {

	@Test
	@DisplayName("scope 설정 시 authorize URL에 이메일 동의(scope=account_email)가 실린다")
	void buildAuthorizeUrl_scope포함() {
		OAuthProperties props = new OAuthProperties(
				"http://localhost:8080/login", List.of(),
				new OAuthProperties.Provider("client-id", "secret", "account_email"),
				new OAuthProperties.Provider("g", "gs", null));
		KakaoOAuthClient client = new KakaoOAuthClient(props, mock(KakaoApi.class));

		String url = client.buildAuthorizeUrl("kakao:state", "http://localhost:3000/login");

		assertThat(url).contains("scope=account_email")
				.contains("client_id=client-id")
				.contains("response_type=code");
	}

	@Test
	@DisplayName("scope 미설정이면 scope 파라미터를 생략한다")
	void buildAuthorizeUrl_scope없음() {
		OAuthProperties props = new OAuthProperties(
				"http://localhost:8080/login", List.of(),
				new OAuthProperties.Provider("client-id", "secret", null),
				new OAuthProperties.Provider("g", "gs", null));
		KakaoOAuthClient client = new KakaoOAuthClient(props, mock(KakaoApi.class));

		String url = client.buildAuthorizeUrl("kakao:state", "http://localhost:3000/login");

		assertThat(url).doesNotContain("scope=");
	}
}
