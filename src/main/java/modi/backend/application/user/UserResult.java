package modi.backend.application.user;

import modi.backend.domain.user.User;

/**
 * 사용자 유스케이스 출력 모음. (Facade는 Result까지만)
 */
public final class UserResult {

	private UserResult() {
	}

	/** 프로필 출력. */
	public record Profile(Long userId, String nickname, boolean profileCompleted) {

		public static Profile from(User user) {
			return new Profile(user.getId(), user.getNickname(), user.isProfileCompleted());
		}
	}
}
