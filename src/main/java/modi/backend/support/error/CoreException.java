package modi.backend.support.error;

/**
 * 애플리케이션 전역 예외. 항상 {@link ErrorCode}를 들고 다녀 전역 핸들러가 HTTP·응답으로 매핑한다.
 * 클라이언트엔 {@code errorCode.message()}만 노출하고, {@code detail}은 로그·디버깅용이다.
 */
public class CoreException extends RuntimeException {

	private final transient ErrorCode errorCode;

	public CoreException(ErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	/** 로그용 상세 메시지(원인 등)를 덧붙인다. 클라이언트엔 여전히 errorCode.message()만 노출. */
	public CoreException(ErrorCode errorCode, String detail) {
		super(detail);
		this.errorCode = errorCode;
	}

	public CoreException(ErrorCode errorCode, String detail, Throwable cause) {
		super(detail, cause);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}
}
