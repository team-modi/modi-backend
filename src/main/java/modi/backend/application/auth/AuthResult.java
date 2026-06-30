package modi.backend.application.auth;

import modi.backend.domain.auth.AuthTokens;
import modi.backend.domain.user.SocialAccount;
import modi.backend.domain.user.User;

/**
 * 인증 유스케이스 출력 모음. (Facade는 Result까지만)
 */
public final class AuthResult {

	private AuthResult() {
	}

	/**
	 * 로그인/재발급 출력. provider = 이번 로그인 provider, email = 해당 소셜계정 이메일(없으면 null).
	 */
	public record Login(Long userId, String nickname, boolean profileCompleted, String provider, String email,
						String accessToken, String refreshToken) {

		public static Login of(User user, String provider, String email, AuthTokens tokens) {
			return new Login(user.getId(), user.getNickname(), user.isProfileCompleted(), provider, email,
					tokens.accessToken(), tokens.refreshToken());
		}
	}

	/** 소셜 계정 연동 출력. 로그인 유저에 provider를 추가 연결한 결과. */
	public record Link(Long userId, String provider, String email) {

		public static Link from(SocialAccount social) {
			return new Link(social.getUserId(), social.getProvider(), social.getEmail());
		}
	}
}
