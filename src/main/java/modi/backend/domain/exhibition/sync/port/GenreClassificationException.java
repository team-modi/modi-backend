package modi.backend.domain.exhibition.sync.port;

/**
 * 장르 분류 실패 — {@link GenreClassifier} 구현이 유효한 분류를 만들지 못했을 때 던진다(ADR-11 계약 반전).
 *
 * <p>과거 계약("어떤 경우에도 유효 장르 반환")은 실패를 랜덤 폴백값으로 가렸고, 호출부가 provider 표식으로
 * 되분류하는 우회를 낳았다. 이제 실패는 값이 아니라 <b>예외</b>다 — 호출부(아웃박스 처리기)가 메시지를
 * RETRYABLE로 남겨 durable 재시도하고, draft는 분류될 때까지 승격을 대기한다(모든 AI 동시 장애 감수 — 사용자 확정).
 */
public class GenreClassificationException extends RuntimeException {

	public GenreClassificationException(String message) {
		super(message);
	}

	public GenreClassificationException(String message, Throwable cause) {
		super(message, cause);
	}
}
