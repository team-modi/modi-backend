package modi.backend.support.error;

import org.springframework.http.HttpStatus;

/**
 * 에러 식별·메시지·HTTP 매핑의 단일 계약. (CLAUDE.md §예외)
 * 공통은 {@link ErrorType}, 도메인별은 {@code domain/{d}/XxxErrorCode}가 구현한다.
 * 메시지는 구현 enum에 한 곳으로 모아 흩어지지 않게 한다.
 */
public interface ErrorCode {

	/** 클라이언트·로그에서 식별하는 안정적 코드 (예: INVALID_REFRESH). */
	String code();

	/** 클라이언트에 노출하는 사용자 메시지(민감정보 금지). */
	String message();

	/** HTTP 매핑. (HTTP 누수는 의도된 실용 선택) */
	HttpStatus getStatus();
}
