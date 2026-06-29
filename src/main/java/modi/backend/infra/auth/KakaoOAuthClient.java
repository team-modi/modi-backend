package modi.backend.infra.auth;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import modi.backend.config.OAuthProperties;
import modi.backend.domain.auth.OAuthUserInfo;
import modi.backend.domain.auth.Provider;

/**
 * 카카오 OAuth 클라이언트. HTTP 호출은 {@link KakaoApi}(HTTP Interface) 위임.
 * userinfo가 중첩 구조(kakao_account.email, .profile.nickname).
 */
@Component
public class KakaoOAuthClient extends AbstractOAuthClient {

	private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";

	private final OAuthProperties.Provider props;
	private final KakaoApi kakaoApi;

	public KakaoOAuthClient(OAuthProperties properties, KakaoApi kakaoApi) {
		this.props = properties.kakao();
		this.kakaoApi = kakaoApi;
	}

	@Override
	public Provider provider() {
		return Provider.KAKAO;
	}

	@Override
	public String buildAuthorizeUrl(String state, String redirectUri) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
				.queryParam("client_id", props.clientId())
				.queryParam("redirect_uri", redirectUri)
				.queryParam("response_type", "code")
				.queryParam("state", state);
		// 이메일 등 동의항목 요청(콘솔에 활성화돼 있어야 함). 예: account_email
		if (props.scope() != null && !props.scope().isBlank()) {
			builder.queryParam("scope", props.scope());
		}
		return builder.build().encode().toUriString();
	}

	@Override
	@SuppressWarnings("unchecked")
	public OAuthUserInfo fetchUserInfo(String code, String redirectUri) {
		Map<String, Object> token = kakaoApi.getToken(
				tokenForm(props.clientId(), props.clientSecret(), redirectUri, code));
		Map<String, Object> body = kakaoApi.getUserInfo("Bearer " + extractAccessToken(token));

		String sub = String.valueOf(body.get("id"));
		Map<String, Object> account = (Map<String, Object>) body.getOrDefault("kakao_account", Map.of());
		String email = (String) account.get("email"); // 비동의 시 null
		Map<String, Object> profile = (Map<String, Object>) account.getOrDefault("profile", Map.of());
		String nickname = (String) profile.get("nickname");
		if (nickname == null) {
			Map<String, Object> properties = (Map<String, Object>) body.getOrDefault("properties", Map.of());
			nickname = (String) properties.get("nickname");
		}
		return new OAuthUserInfo(sub, email, nickname);
	}
}
