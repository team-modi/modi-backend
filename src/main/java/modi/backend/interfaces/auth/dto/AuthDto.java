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

	/** 로그인/재발급 응답. access·refresh는 HttpOnly 쿠키로 내려가고, accessToken은 비쿠키 클라이언트 호환용으로 본문에도 둔다. */
	public record TokenResponse(String accessToken, User user) {

		public record User(Long userId, String nickname, boolean profileCompleted, String provider, String email) {
		}

		public static TokenResponse from(AuthResult.Login result) {
			return new TokenResponse(
					result.accessToken(),
					new User(result.userId(), result.nickname(), result.profileCompleted(),
							result.provider(), result.email()));
		}
	}
}
