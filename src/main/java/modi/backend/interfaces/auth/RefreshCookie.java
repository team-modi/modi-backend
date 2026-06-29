package modi.backend.interfaces.auth;

/**
 * refresh 토큰을 담는 HttpOnly 쿠키 빌더. secure/SameSite는 환경별로 주입(app.auth.cookie.*).
 *
 * 운영은 Vercel 프록시(yeowun.vercel.app/api/*)로 same-origin이 되므로 SameSite=Lax + Secure면 충분.
 * (FE/BE를 cross-site로 직결한다면 SameSite=None + Secure + BE HTTPS 필요)
 */
final class RefreshCookie {

	static final String NAME = "refresh_token";

	private RefreshCookie() {
	}

	static String build(String refreshToken, long maxAgeSeconds, boolean secure, String sameSite) {
		StringBuilder sb = new StringBuilder()
				.append(NAME).append('=').append(refreshToken)
				.append("; Max-Age=").append(maxAgeSeconds)
				.append("; Path=/; HttpOnly; SameSite=").append(sameSite);
		if (secure) {
			sb.append("; Secure");
		}
		return sb.toString();
	}
}
