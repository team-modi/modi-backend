package modi.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth 설정 바인딩. {@code app.oauth.*}.
 * client-id는 공개성 값, client-secret은 환경변수로만 주입.
 * allowed-redirect-uris는 FE 주도 플로우에서 받은 redirectUri 화이트리스트(보안).
 */
@ConfigurationProperties(prefix = "app.oauth")
public record OAuthProperties(String redirectUri, List<String> allowedRedirectUris,
							  Provider kakao, Provider google, Provider naver) {

	public OAuthProperties {
		if (allowedRedirectUris == null) {
			allowedRedirectUris = List.of();
		}
	}

	public boolean isAllowedRedirectUri(String uri) {
		return allowedRedirectUris.contains(uri);
	}

	/** scope: provider 동의항목(예: 카카오 {@code account_email}). 비면 authorize에서 생략. */
	public record Provider(String clientId, String clientSecret, String scope) {
	}
}
