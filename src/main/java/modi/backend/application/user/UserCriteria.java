package modi.backend.application.user;

/**
 * 사용자 유스케이스 입력 모음. (Request →[Controller] Criteria → Facade)
 */
public final class UserCriteria {

	private UserCriteria() {
	}

	/** 온보딩/프로필 수정 입력. userId는 인증에서, nickname은 요청에서 채운다. */
	public record ProfileUpdate(Long userId, String nickname) {
	}
}
