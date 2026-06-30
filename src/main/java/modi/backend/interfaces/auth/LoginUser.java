package modi.backend.interfaces.auth;

import modi.backend.domain.auth.TokenClaims;

/**
 * 인증된 사용자(access 토큰 클레임 기반). 컨트롤러가 @Authentication으로 주입받는다.
 */
public record LoginUser(Long userId, String provider, String nickname, Boolean profileCompleted) {

	public static LoginUser from(TokenClaims claims) {
		return new LoginUser(claims.userId(), claims.provider(), claims.nickname(), claims.profileCompleted());
	}
}
