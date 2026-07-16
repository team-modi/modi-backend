package modi.backend.application.auth;

/**
 * 인증 유스케이스 입력 모음. (Request →[Controller] Criteria → Facade)
 */
public final class AuthCriteria {

	private AuthCriteria() {
	}

	/**
	 * 소셜 로그인 입력: provider(path) + code·redirectUri·state(body).
	 * state는 네이버 토큰 교환에만 쓰인다(카카오/구글은 무시). 카카오/구글 요청은 state가 null일 수 있다.
	 */
	public record Login(String provider, String code, String redirectUri, String state) {
	}
}
