package modi.backend.interfaces.auth;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import modi.backend.interfaces.auth.dto.AuthDto;
import modi.backend.support.response.ApiResponse;

/**
 * 인증 API Swagger 스펙. (MVC 어노테이션은 {@link AuthV1Controller}, 문서 어노테이션은 여기)
 */
@Tag(name = "Auth", description = "소셜 로그인 · JWT 발급/재발급 · 내 정보")
public interface AuthV1ApiSpec {

	@Operation(summary = "소셜 로그인 (가입 겸용)", description = """
			FE 주도 플로우. FE가 소셜 인가를 마친 뒤 콜백에서 받은 authorization code를 그대로 보낸다.
			- Path `provider`: `kakao` | `google` (소문자).
			- Body: `code`(소셜 인가 코드), `redirectUri`(소셜 콘솔·서버 화이트리스트에 등록된 값과 정확히 일치해야 함. 불일치 시 400 INVALID_REDIRECT_URI).
			- 최초 로그인이면 회원을 자동 생성(가입 겸용), 이후엔 기존 회원으로 로그인.
			- 응답: `access`·`refresh` 토큰을 **HttpOnly 쿠키**로 내려준다. 브라우저 클라이언트는 이후 요청에 `credentials: 'include'`만 하면 되고 토큰을 직접 저장할 필요가 없다. 비쿠키 클라이언트(모바일 등) 호환을 위해 `accessToken`은 응답 본문에도 함께 반환한다.
			- 성공은 항상 200(프로젝트 컨벤션).""")
	ResponseEntity<ApiResponse<AuthDto.TokenResponse>> login(
			@Parameter(in = ParameterIn.PATH, description = "소셜 provider — kakao | google", example = "kakao") String provider,
			AuthDto.LoginRequest request,
			@Parameter(hidden = true) HttpServletResponse response);

	@Operation(summary = "토큰 재발급", description = """
			access 토큰이 만료(401)됐을 때 호출한다. `refresh_token` **쿠키**를 읽어 검증하고 access·refresh를 회전 발급해 두 쿠키를 다시 내려준다.
			- 별도 요청 body 없음(쿠키만 필요). `credentials: 'include'`로 호출.
			- refresh 쿠키가 없으면 401, 만료·폐기(로그아웃됨)됐으면 401 INVALID_REFRESH_TOKEN → 재로그인 유도.
			- access 토큰은 응답 본문에도 호환용으로 반환.""")
	ResponseEntity<ApiResponse<AuthDto.TokenResponse>> refresh(
			@Parameter(hidden = true) String refreshToken,
			@Parameter(hidden = true) HttpServletResponse response);

	@Operation(summary = "로그아웃", description = """
			access·refresh 쿠키를 즉시 만료(Max-Age=0)시키고 서버에 저장된 해당 사용자의 refresh 토큰을 폐기한다.
			- 이후 그 refresh 토큰으로 재발급을 시도하면 401. 멱등(토큰이 없거나 이미 무효여도 200).
			- `credentials: 'include'`로 호출.""")
	ResponseEntity<ApiResponse<Object>> logout(
			@Parameter(hidden = true) String refreshToken,
			@Parameter(hidden = true) HttpServletResponse response);
}
