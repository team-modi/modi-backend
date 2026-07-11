package modi.backend.interfaces.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import modi.backend.interfaces.admin.dto.AdminAuthDto;
import modi.backend.support.response.ApiResponse;

@Tag(name = "Admin - Auth", description = "관리자 콘솔 로그인(이메일 화이트리스트 + 공용 비밀번호). 성공 시 admin_session 쿠키 발급.")
public interface AdminAuthV1ApiSpec {

	@Operation(summary = "관리자 로그인", description = "이메일이 화이트리스트에 있고 비밀번호가 맞으면 admin_session 쿠키를 발급한다.")
	ApiResponse<Object> login(AdminAuthDto.LoginRequest request, @Parameter(hidden = true) HttpServletResponse response);

	@Operation(summary = "관리자 로그아웃", description = "admin_session 쿠키를 만료시킨다.")
	ApiResponse<Object> logout(@Parameter(hidden = true) HttpServletResponse response);

	@Operation(summary = "세션 확인", description = "유효한 admin_session이면 200(로그인 상태), 아니면 401.")
	ApiResponse<Object> me();
}
