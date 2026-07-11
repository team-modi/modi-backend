package modi.backend.interfaces.admin;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import modi.backend.application.admin.AdminAuthFacade;
import modi.backend.interfaces.admin.dto.AdminAuthDto;
import modi.backend.support.response.ApiResponse;

/**
 * 관리자 콘솔 로그인/로그아웃/세션확인. `/login`은 게이트에서 제외(그래야 로그인 가능), `/logout`·`/me`는 세션 필요.
 * ⚠️ 운영은 http(무TLS)라 쿠키·비번이 평문 — 내부 베타 전제. HTTPS 승격 시 secure 쿠키로.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1")
public class AdminAuthV1Controller implements AdminAuthV1ApiSpec {

	private final AdminAuthFacade adminAuthFacade;
	private final AdminSession adminSession;

	@Override
	@PostMapping("/login")
	public ApiResponse<Object> login(@RequestBody @Valid AdminAuthDto.LoginRequest request,
			HttpServletResponse response) {
		String email = adminAuthFacade.authenticate(request.email(), request.password());
		response.addHeader(HttpHeaders.SET_COOKIE,
				sessionCookie(adminSession.issue(email), AdminSession.TTL.getSeconds()).toString());
		return ApiResponse.success();
	}

	@Override
	@PostMapping("/logout")
	public ApiResponse<Object> logout(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", 0).toString());
		return ApiResponse.success();
	}

	@Override
	@GetMapping("/me")
	public ApiResponse<Object> me() {
		return ApiResponse.success(); // 여기 도달 = 인터셉터 통과 = 세션 유효
	}

	// 운영 http라 secure=false(안 그러면 쿠키 전송 안 됨). HttpOnly로 JS 접근은 차단.
	private static ResponseCookie sessionCookie(String value, long maxAgeSeconds) {
		return ResponseCookie.from(AdminSession.SESSION_COOKIE, value)
				.httpOnly(true).path("/").maxAge(maxAgeSeconds).sameSite("Lax").build();
	}
}
