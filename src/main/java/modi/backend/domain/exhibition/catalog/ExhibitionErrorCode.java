package modi.backend.domain.exhibition.catalog;

import org.springframework.http.HttpStatus;

import modi.backend.support.error.ErrorCode;

/**
 * 전시 도메인 에러. 메시지는 여기 한 곳에서 정의한다. (03_전시.md 3.3 Error Cases)
 * 공통 성격(INVALID_INPUT·UNAUTHORIZED·FORBIDDEN)은 {@link modi.backend.support.error.ErrorType} 재사용.
 */
public enum ExhibitionErrorCode implements ErrorCode {

	EXHIBITION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 전시를 찾을 수 없습니다."),
	EXTERNAL_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "EXTERNAL_API_UNAVAILABLE",
			"외부 전시 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	ExhibitionErrorCode(HttpStatus status, String code, String message) {
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
