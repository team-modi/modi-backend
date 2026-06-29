package modi.backend.interfaces.auth;

import modi.backend.domain.auth.AuthErrorCode;
import modi.backend.support.error.CoreException;

/**
 * Authorization 헤더에서 Bearer access 토큰만 뽑아내는 인터페이스 계층 헬퍼.
 * (헤더 파싱은 인터페이스 책임 — 검증은 Facade.requireAccess)
 */
public final class BearerToken {

	private static final String PREFIX = "Bearer ";

	private BearerToken() {
	}

	/** "Bearer xxx" → "xxx". 없거나 형식이 다르면 {@link AuthErrorCode#NO_ACCESS_TOKEN}. */
	public static String resolve(String authorizationHeader) {
		if (authorizationHeader == null || !authorizationHeader.startsWith(PREFIX)) {
			throw new CoreException(AuthErrorCode.NO_ACCESS_TOKEN);
		}
		return authorizationHeader.substring(PREFIX.length());
	}
}
