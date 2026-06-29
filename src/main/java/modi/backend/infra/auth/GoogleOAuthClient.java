package modi.backend.infra.auth;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import modi.backend.config.OAuthProperties;
import modi.backend.domain.auth.OAuthUserInfo;
import modi.backend.domain.auth.Provider;

/**
 * 구글 OAuth 클라이언트. HTTP 호출은 {@link GoogleApi}(HTTP Interface) 위임.
 * userinfo가 플랫 구조(email, name).
 */
@Component
public class GoogleOAuthClient extends AbstractOAuthClient {

	private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";

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
	public String buildAuthorizeUrl(String state, String redirectUri) {
		return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
				.queryParam("client_id", props.clientId())
				.queryParam("redirect_uri", redirectUri)
				.queryParam("response_type", "code")
				.queryParam("scope", "openid email profile")
				.queryParam("state", state)
				.queryParam("access_type", "offline")
				.queryParam("prompt", "consent")
				.build().encode().toUriString();
	}

	@Override
	public OAuthUserInfo fetchUserInfo(String code, String redirectUri) {
		Map<String, Object> token = googleApi.getToken(
				tokenForm(props.clientId(), props.clientSecret(), redirectUri, code));
		Map<String, Object> body = googleApi.getUserInfo("Bearer " + extractAccessToken(token));
		return new OAuthUserInfo(
				String.valueOf(body.get("id")),
				(String) body.get("email"),
				(String) body.get("name"));
	}
}
