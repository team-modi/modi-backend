package modi.backend.support.response;

import java.util.List;

/**
 * 공통 API 응답 래퍼. (CLAUDE.md 컨벤션: ApiResponse.success/fail/failValidation)
 * 성공은 전부 200 — 에러 코드/메시지로 구분한다. fieldErrors는 검증 실패 시에만 채운다.
 */
public record ApiResponse<T>(boolean success, T data, String code, String message, List<FieldError> fieldErrors) {

	/** 필드 단위 검증 오류 한 건. */
	public record FieldError(String field, String reason) {
	}

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null, null, null);
	}

	public static <T> ApiResponse<T> fail(String code, String message) {
		return new ApiResponse<>(false, null, code, message, null);
	}

	public static <T> ApiResponse<T> failValidation(String code, String message, List<FieldError> fieldErrors) {
		return new ApiResponse<>(false, null, code, message, fieldErrors);
	}
}
