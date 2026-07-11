package modi.backend.interfaces.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * access 토큰 추출 공용 유틸(예외 없이). access_token 쿠키 우선, 없으면 Authorization Bearer.
 * 인터셉터처럼 "있으면 쓰고 없으면 넘어가는" 경로에서 쓴다({@link AuthenticationArgumentResolver}는 없으면 401을 던진다).
 */
public final class AccessTokens {

	private static final String BEARER_PREFIX = "Bearer ";

	private AccessTokens() {
	}

	/** 토큰 문자열 or null(둘 다 없거나 비어 있으면). */
	public static String resolveOrNull(HttpServletRequest request) {
		if (request == null) {
			return null;
		}
		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if (AuthCookie.ACCESS.equals(cookie.getName())
						&& cookie.getValue() != null && !cookie.getValue().isBlank()) {
					return cookie.getValue();
				}
			}
		}
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			String token = header.substring(BEARER_PREFIX.length());
			return token.isBlank() ? null : token;
		}
		return null;
	}
}
