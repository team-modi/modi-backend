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

	/** 소셜 계정 연동 입력: 로그인 유저(userId) + 추가할 provider·code·redirectUri. */
	public record Link(Long userId, String provider, String code, String redirectUri) {
	}
}
