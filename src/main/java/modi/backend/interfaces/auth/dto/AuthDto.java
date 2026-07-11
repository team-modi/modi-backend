package modi.backend.interfaces.auth.dto;

import jakarta.validation.constraints.NotBlank;
import modi.backend.application.auth.AuthResult;

/**
 * 인증 API의 요청/응답 DTO 모음. (파일 수 절감을 위해 중첩 record로 묶음)
 */
public final class AuthDto {

	private AuthDto() {
	}

	/** 로그인/연동 요청: FE가 provider 콜백에서 받은 code와 사용한 redirectUri. */
	public record LoginRequest(@NotBlank String code, @NotBlank String redirectUri) {
	}

	/**
	 * 휴대폰 식별 게스트 로그인 요청(베타 전용). 하이픈·공백 포함 입력 허용 —
	 * 정규화(숫자만)·형식 규칙(01 시작 10~11자리)은 도메인 VO(PhoneNumber)가 검증한다.
	 */
	public record PhoneGuestLoginRequest(@NotBlank String phoneNumber) {
	}

	/** 로그인/재발급 응답. access·refresh는 HttpOnly 쿠키로 내려가고, accessToken은 비쿠키 클라이언트 호환용으로 본문에도 둔다. */
	public record TokenResponse(String accessToken, User user) {

		/**
		 * 로그인 사용자 요약. 소셜 동의항목에서 받은 name(이름)·ageGroup(연령대)·birthYear(출생연도)를 포함한다.
		 * 미동의/미지원 항목은 null(ageGroup은 UNSPECIFIED → null).
		 */
		public record User(Long userId, String nickname, String name, boolean profileCompleted, String provider,
				String email, String ageGroup, Integer birthYear) {
		}

		public static TokenResponse from(AuthResult.Login result) {
			return new TokenResponse(
					result.accessToken(),
					new User(result.userId(), result.nickname(), result.name(), result.profileCompleted(),
							result.provider(), result.email(), result.ageGroup(), result.birthYear()));
		}
	}
}
