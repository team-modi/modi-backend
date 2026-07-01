package modi.backend.application.auth;

/**
 * 인증 유스케이스 입력 모음. (Request →[Controller] Criteria → Facade)
 */
public final class AuthCriteria {

	private AuthCriteria() {
	}

	/** 소셜 로그인 입력: provider(path) + code·redirectUri(body). */
	public record Login(String provider, String code, String redirectUri) {
	}
}
