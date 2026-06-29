package modi.backend.support.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import modi.backend.support.response.ApiResponse;

/**
 * 전역 예외 → {@link ApiResponse} 매핑. 컨트롤러의 ad-hoc 에러 응답을 한 곳으로 모은다.
 * 메시지는 {@link ErrorCode} 구현 enum이 소유하고, 여기서는 매핑만 한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 도메인/애플리케이션이 의도적으로 던진 예외. errorCode가 HTTP·코드·메시지를 결정한다. */
	@ExceptionHandler(CoreException.class)
	public ResponseEntity<ApiResponse<Object>> handleCore(CoreException e) {
		ErrorCode ec = e.errorCode();
		if (ec.getStatus().is5xxServerError()) {
			log.error("[{}] {}", ec.code(), e.getMessage(), e);
		} else {
			log.warn("[{}] {}", ec.code(), e.getMessage());
		}
		return ResponseEntity.status(ec.getStatus())
				.body(ApiResponse.fail(ec.code(), ec.message()));
	}

	/** @Valid 바디 검증 실패. 필드별 사유를 fieldErrors로 내려준다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<List<ApiResponse.FieldError>>> handleValidation(MethodArgumentNotValidException e) {
		List<ApiResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
				.map(GlobalExceptionHandler::toFieldError)
				.toList();
		return ResponseEntity.status(ErrorType.INVALID_INPUT.getStatus())
				.body(ApiResponse.failValidation(ErrorType.INVALID_INPUT.code(),
						ErrorType.INVALID_INPUT.message(), fieldErrors));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		return ResponseEntity.status(ErrorType.METHOD_NOT_ALLOWED.getStatus())
				.body(ApiResponse.fail(ErrorType.METHOD_NOT_ALLOWED.code(), ErrorType.METHOD_NOT_ALLOWED.message()));
	}

	/** 미처리 예외는 내부 오류로 덮고 상세는 로그로만. (스택/원인 노출 방지) */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception e) {
		log.error("[{}] unhandled", ErrorType.INTERNAL_ERROR.code(), e);
		return ResponseEntity.status(ErrorType.INTERNAL_ERROR.getStatus())
				.body(ApiResponse.fail(ErrorType.INTERNAL_ERROR.code(), ErrorType.INTERNAL_ERROR.message()));
	}

	private static ApiResponse.FieldError toFieldError(FieldError fe) {
		return new ApiResponse.FieldError(fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage());
	}
}
