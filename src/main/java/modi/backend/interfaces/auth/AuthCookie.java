package modi.backend.interfaces.auth;

/**
 * 인증 토큰을 담는 HttpOnly 쿠키 빌더. access·refresh 공용. secure/SameSite는 환경별로 주입(app.auth.cookie.*).
 *
 * access·refresh 모두 HttpOnly로 내려 JS가 못 읽게 한다(XSS로 토큰 탈취 차단). FE는 쿠키 값을 직접 읽지 않고
 * credentials=include 로 자동 동봉만 한다 — 내 정보는 {@code /api/v1/users/me}로 조회.
 *
 * 운영은 Vercel 프록시(yeowun.vercel.app/api/*)로 same-origin이 되므로 SameSite=Lax + Secure면 충분.
 * (FE/BE를 cross-site로 직결한다면 SameSite=None + Secure + BE HTTPS 필요)
 */
final class AuthCookie {

	static final String ACCESS = "access_token";
	static final String REFRESH = "refresh_token";

	private AuthCookie() {
	}

	static String build(String name, String value, long maxAgeSeconds, boolean secure, String sameSite) {
		StringBuilder sb = new StringBuilder()
				.append(name).append('=').append(value)
				.append("; Max-Age=").append(maxAgeSeconds)
				.append("; Path=/; HttpOnly; SameSite=").append(sameSite);
		if (secure) {
			sb.append("; Secure");
		}
		return sb.toString();
	}

	/** 즉시 만료(Max-Age=0, 빈 값)시키는 쿠키 — 로그아웃에 사용. set 때와 같은 Path/속성이어야 삭제된다. */
	static String expire(String name, boolean secure, String sameSite) {
		return build(name, "", 0, secure, sameSite);
	}
}
