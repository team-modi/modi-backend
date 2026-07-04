package modi.backend.domain.ai;

import org.springframework.http.HttpStatus;

import modi.backend.support.error.ErrorCode;

public enum AiErrorCode implements ErrorCode {

	AI_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED", "AI 기능이 설정되지 않았습니다."),
	AI_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI_GENERATION_FAILED", "AI 응답 생성에 실패했습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	AiErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	@Override
	public HttpStatus getStatus() {
		return status;
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public String message() {
		return message;
	}
}
