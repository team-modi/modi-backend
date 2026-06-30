package modi.backend.infra.auth;

import java.util.Map;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import modi.backend.domain.auth.AuthErrorCode;
import modi.backend.domain.auth.OAuthClient;
import modi.backend.support.error.CoreException;

/**
 * provider 공통 헬퍼(토큰 요청 폼·access token 추출). 실제 HTTP는 HTTP Interface 클라이언트가 수행.
 */
abstract class AbstractOAuthClient implements OAuthClient {

	protected MultiValueMap<String, String> tokenForm(String clientId, String clientSecret,
													  String redirectUri, String code) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", clientId);
		form.add("client_secret", clientSecret);
		form.add("redirect_uri", redirectUri);
		form.add("code", code);
		return form;
	}

	protected String extractAccessToken(Map<String, Object> tokenResponse) {
		Object token = tokenResponse == null ? null : tokenResponse.get("access_token");
		if (token == null) {
			throw new CoreException(AuthErrorCode.OAUTH_COMMUNICATION_FAILED,
					"토큰 교환 실패(" + provider().code() + "): " + tokenResponse);
		}
		return (String) token;
	}
}
