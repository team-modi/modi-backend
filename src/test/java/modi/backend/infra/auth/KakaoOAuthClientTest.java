package modi.backend.infra.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.config.OAuthProperties;

class KakaoOAuthClientTest {

	@Test
	@DisplayName("설정한 동의항목(이메일·연령대·출생연도)이 authorize URL scope에 모두 실린다")
	void buildAuthorizeUrl_scope포함() {
		OAuthProperties props = new OAuthProperties(
				"http://localhost:8080/login", List.of(),
				new OAuthProperties.Provider("client-id", "secret", "account_email,age_range,birthyear"),
				new OAuthProperties.Provider("g", "gs", null));
		KakaoOAuthClient client = new KakaoOAuthClient(props, mock(KakaoApi.class));

		String url = client.buildAuthorizeUrl("kakao:state", "http://localhost:3000/login");

		assertThat(url).contains("account_email")
				.contains("age_range")
				.contains("birthyear")
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
