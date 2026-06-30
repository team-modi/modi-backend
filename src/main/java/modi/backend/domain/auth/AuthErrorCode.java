package modi.backend.domain.auth;

import org.springframework.http.HttpStatus;

import modi.backend.support.error.ErrorCode;

/**
 * 인증/소셜 로그인 도메인 에러. 메시지는 여기 한 곳에서 정의해 흩어지지 않게 한다.
 * HTTP 매핑을 들고 있는 건 컨벤션상 의도된 실용 선택(§예외).
 */
public enum AuthErrorCode implements ErrorCode {

	UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER", "지원하지 않는 소셜 로그인입니다."),
	INVALID_REDIRECT_URI(HttpStatus.BAD_REQUEST, "INVALID_REDIRECT_URI", "허용되지 않은 redirectUri입니다."),
	INVALID_STATE(HttpStatus.BAD_REQUEST, "INVALID_STATE", "유효하지 않은 인증 요청입니다."),
	OAUTH_COMMUNICATION_FAILED(HttpStatus.BAD_GATEWAY, "OAUTH_COMMUNICATION_FAILED", "소셜 로그인 처리 중 오류가 발생했습니다."),
	NO_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "NO_ACCESS_TOKEN", "인증 토큰이 없습니다."),
	INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "유효하지 않은 인증 토큰입니다."),
	NO_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "NO_REFRESH_TOKEN", "재발급 토큰이 없습니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "재발급 토큰 검증에 실패했습니다."),
	SOCIAL_ACCOUNT_LINK_BROKEN(HttpStatus.INTERNAL_SERVER_ERROR, "SOCIAL_ACCOUNT_LINK_BROKEN", "연결된 사용자 정보를 찾을 수 없습니다."),
	SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "SOCIAL_ACCOUNT_ALREADY_LINKED", "이미 다른 계정에 연결된 소셜 로그인입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	AuthErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public String message() {
		return message;
	}

	@Override
	public HttpStatus getStatus() {
		return status;
	}
}
