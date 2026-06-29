package modi.backend.interfaces.auth;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import modi.backend.interfaces.auth.dto.AuthDto;
import modi.backend.support.response.ApiResponse;

/**
 * 인증 API Swagger 스펙. (MVC 어노테이션은 {@link AuthV1Controller}, 문서 어노테이션은 여기)
 */
@Tag(name = "Auth", description = "소셜 로그인 · JWT 발급/재발급 · 내 정보 · 계정 연동")
public interface AuthV1ApiSpec {

	@Operation(summary = "소셜 로그인", description = "FE가 provider 콜백에서 받은 code로 로그인. access는 본문, refresh는 HttpOnly 쿠키.")
	ResponseEntity<ApiResponse<AuthDto.TokenResponse>> login(
			@Parameter(in = ParameterIn.PATH, description = "kakao | google") String provider,
			AuthDto.LoginRequest request,
			@Parameter(hidden = true) HttpServletResponse response);

	@Operation(summary = "토큰 재발급", description = "refresh 쿠키로 access/refresh 회전 발급.")
	ResponseEntity<ApiResponse<AuthDto.TokenResponse>> refresh(
			@Parameter(hidden = true) String refreshToken,
			@Parameter(hidden = true) HttpServletResponse response);

	@Operation(summary = "내 정보", description = "access 토큰 기반 사용자 정보.")
	@SecurityRequirement(name = "bearerAuth")
	ResponseEntity<ApiResponse<AuthDto.MeResponse>> me(
			@Parameter(hidden = true) LoginUser user);

	@Operation(summary = "소셜 계정 연동", description = "로그인 유저에 다른 provider 소셜 계정을 추가 연결.")
	@SecurityRequirement(name = "bearerAuth")
	ResponseEntity<ApiResponse<AuthDto.LinkResponse>> link(
			@Parameter(in = ParameterIn.PATH, description = "추가 연결할 provider (kakao | google)") String provider,
			@Parameter(hidden = true) LoginUser user,
			AuthDto.LoginRequest request);
}
