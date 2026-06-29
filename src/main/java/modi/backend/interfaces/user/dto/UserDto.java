package modi.backend.interfaces.user.dto;

import jakarta.validation.constraints.NotBlank;
import modi.backend.application.user.UserResult;

/**
 * 사용자 API의 요청/응답 DTO 모음. (파일 수 절감을 위해 중첩 record로 묶음)
 */
public final class UserDto {

	private UserDto() {
	}

	/** 온보딩/프로필 수정 요청. (프로필 항목은 추후 확장) */
	public record ProfileRequest(@NotBlank String nickname) {
	}

	/**
	 * 프로필 응답. profileCompleted=true가 되면 FE는 온보딩을 종료한다.
	 * (access 토큰의 profileCompleted 클레임은 다음 refresh에서 갱신)
	 */
	public record ProfileResponse(Long userId, String nickname, boolean profileCompleted) {

		public static ProfileResponse from(UserResult.Profile result) {
			return new ProfileResponse(result.userId(), result.nickname(), result.profileCompleted());
		}
	}
}
