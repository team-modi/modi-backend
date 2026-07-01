package modi.backend.infra.auth;

import java.util.Map;

import org.springframework.stereotype.Component;

import modi.backend.config.OAuthProperties;
import modi.backend.domain.auth.OAuthUserInfo;
import modi.backend.domain.auth.Provider;
import modi.backend.domain.user.AgeGroup;

/**
 * 구글 OAuth 클라이언트. HTTP 호출은 {@link GoogleApi}(HTTP Interface) 위임.
 * userinfo가 플랫 구조(email, name).
 */
@Component
public class GoogleOAuthClient extends AbstractOAuthClient {

	private final OAuthProperties.Provider props;
	private final GoogleApi googleApi;

	public GoogleOAuthClient(OAuthProperties properties, GoogleApi googleApi) {
		this.props = properties.google();
		this.googleApi = googleApi;
	}

	@Override
	public Provider provider() {
		return Provider.GOOGLE;
	}

	@Override
	public OAuthUserInfo fetchUserInfo(String code, String redirectUri) {
		Map<String, Object> token = googleApi.getToken(
				tokenForm(props.clientId(), props.clientSecret(), redirectUri, code));
		Map<String, Object> body = googleApi.getUserInfo("Bearer " + extractAccessToken(token));
		// 구글 기본 프로필엔 연령대·출생연도가 없다 → UNSPECIFIED·null.
		return new OAuthUserInfo(
				String.valueOf(body.get("id")),
				(String) body.get("email"),
				(String) body.get("name"),
				AgeGroup.UNSPECIFIED,
				null);
	}
}
