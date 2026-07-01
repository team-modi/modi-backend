package modi.backend.interfaces.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import modi.backend.application.auth.AuthCriteria;
import modi.backend.application.auth.AuthFacade;
import modi.backend.application.auth.AuthResult;
import modi.backend.config.CookieProperties;
import modi.backend.config.OAuthProperties;
import modi.backend.domain.auth.AuthErrorCode;
import modi.backend.interfaces.auth.dto.AuthDto;
import modi.backend.support.error.CoreException;
import modi.backend.support.response.ApiResponse;

/**
 * FE 주도 소셜 로그인 API. (성공 전부 200, access·refresh 모두 HttpOnly 쿠키)
 * FE가 provider 콜백에서 받은 code를 보내면 자체 JWT를 발급한다.
 * access 토큰은 쿠키로 내려가고 인증도 쿠키로 검증한다(본문 accessToken은 비쿠키 클라이언트 호환용으로 함께 반환).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthV1Controller implements AuthV1ApiSpec {

	private final AuthFacade authFacade;
	private final OAuthProperties oauthProperties;
	private final CookieProperties cookieProperties;

	@Override
	@PostMapping("/login/{provider}")
	public ResponseEntity<ApiResponse<AuthDto.TokenResponse>> login(
			@PathVariable String provider,
			@Valid @RequestBody AuthDto.LoginRequest request,
			HttpServletResponse response) {
		// redirectUri 화이트리스트(보안): FE가 보낸 값을 그대로 신뢰하지 않는다.
		if (!oauthProperties.isAllowedRedirectUri(request.redirectUri())) {
			throw new CoreException(AuthErrorCode.INVALID_REDIRECT_URI, "허용 외 redirectUri: " + request.redirectUri());
		}
		AuthResult.Login result = authFacade.login(new AuthCriteria.Login(provider, request.code(), request.redirectUri()));
		setAuthCookies(response, result);
		return ResponseEntity.ok(ApiResponse.success(AuthDto.TokenResponse.from(result)));
	}

	@Override
	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<AuthDto.TokenResponse>> refresh(
			@CookieValue(name = "refresh_token", required = false) String refreshToken,
			HttpServletResponse response) {
		if (refreshToken == null) {
			throw new CoreException(AuthErrorCode.NO_REFRESH_TOKEN);
		}
		AuthResult.Login result = authFacade.reissue(refreshToken);
		setAuthCookies(response, result);
		return ResponseEntity.ok(ApiResponse.success(AuthDto.TokenResponse.from(result)));
	}

	@Override
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Object>> logout(
			@CookieValue(name = "refresh_token", required = false) String refreshToken,
			HttpServletResponse response) {
		authFacade.logout(refreshToken);
		expireAuthCookies(response);
		return ResponseEntity.ok(ApiResponse.success());
	}

	/** access·refresh 쿠키를 즉시 만료시킨다(로그아웃). */
	private void expireAuthCookies(HttpServletResponse response) {
		response.addHeader("Set-Cookie", AuthCookie.expire(
				AuthCookie.ACCESS, cookieProperties.secure(), cookieProperties.sameSite()));
		response.addHeader("Set-Cookie", AuthCookie.expire(
				AuthCookie.REFRESH, cookieProperties.secure(), cookieProperties.sameSite()));
	}

	/** access·refresh를 각각 HttpOnly 쿠키로 내려준다(Max-Age는 토큰 TTL과 일치). */
	private void setAuthCookies(HttpServletResponse response, AuthResult.Login result) {
		response.addHeader("Set-Cookie", AuthCookie.build(AuthCookie.ACCESS,
				result.accessToken(), authFacade.accessTtlSeconds(),
				cookieProperties.secure(), cookieProperties.sameSite()));
		response.addHeader("Set-Cookie", AuthCookie.build(AuthCookie.REFRESH,
				result.refreshToken(), authFacade.refreshTtlSeconds(),
				cookieProperties.secure(), cookieProperties.sameSite()));
	}
}
