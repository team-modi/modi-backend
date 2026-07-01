package modi.backend.domain.user;

import org.springframework.http.HttpStatus;

import modi.backend.support.error.ErrorCode;

/**
 * 사용자 도메인 에러. 메시지는 여기 한 곳에서 정의한다.
 */
public enum UserErrorCode implements ErrorCode {

	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
	INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "닉네임은 1~20자, 공백만으로 구성할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	UserErrorCode(HttpStatus status, String code, String message) {
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
