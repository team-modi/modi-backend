package modi.backend.interfaces.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 관리자 콘솔 로그인 요청. */
public final class AdminAuthDto {

	private AdminAuthDto() {
	}

	public record LoginRequest(
			@Schema(description = "화이트리스트에 등록된 관리자 이메일", example = "byeongpilseo@gmail.com")
			@NotBlank @Email String email,
			@Schema(description = "공용 관리자 비밀번호")
			@NotBlank String password) {
	}
}
