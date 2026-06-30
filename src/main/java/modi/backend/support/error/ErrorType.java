package modi.backend.support.error;

import org.springframework.http.HttpStatus;

/**
 * 도메인에 속하지 않는 공통 에러. (검증 실패·미지원 메서드·서버 오류 등)
 * 도메인 고유 에러는 {@code domain/{d}/XxxErrorCode}에 둔다.
 */
public enum ErrorType implements ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다."),
	INVALID_MEDIA(HttpStatus.BAD_REQUEST, "INVALID_MEDIA", "첨부할 수 없는 미디어입니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "허용되지 않은 요청 방식입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	ErrorType(HttpStatus status, String code, String message) {
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
