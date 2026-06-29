package modi.backend.interfaces.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
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
 * FE 주도 소셜 로그인 API. (성공 전부 200, refresh는 HttpOnly 쿠키)
 * FE가 provider 콜백에서 받은 code를 보내면 자체 JWT를 발급한다.
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
		setRefreshCookie(response, result.refreshToken());
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
		setRefreshCookie(response, result.refreshToken());
		return ResponseEntity.ok(ApiResponse.success(AuthDto.TokenResponse.from(result)));
	}

	@Override
	@GetMapping("/me")
	public ResponseEntity<ApiResponse<AuthDto.MeResponse>> me(@Authentication LoginUser user) {
		return ResponseEntity.ok(ApiResponse.success(AuthDto.MeResponse.from(user)));
	}

	/** 로그인 유저에 다른 provider 소셜 계정을 추가 연동(access 토큰 필요). */
	@Override
	@PostMapping("/link/{provider}")
	public ResponseEntity<ApiResponse<AuthDto.LinkResponse>> link(
			@PathVariable String provider,
			@Authentication LoginUser user,
			@Valid @RequestBody AuthDto.LoginRequest request) {
		if (!oauthProperties.isAllowedRedirectUri(request.redirectUri())) {
			throw new CoreException(AuthErrorCode.INVALID_REDIRECT_URI, "허용 외 redirectUri: " + request.redirectUri());
		}
		AuthResult.Link result = authFacade.link(
				new AuthCriteria.Link(user.userId(), provider, request.code(), request.redirectUri()));
		return ResponseEntity.ok(ApiResponse.success(AuthDto.LinkResponse.from(result)));
	}

	private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
		response.addHeader("Set-Cookie", RefreshCookie.build(
				refreshToken, authFacade.refreshTtlSeconds(),
				cookieProperties.secure(), cookieProperties.sameSite()));
	}
}
