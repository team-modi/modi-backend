package modi.backend.domain.exhibition;

/**
 * 보강 작업 실패의 분류 — {@link EnrichmentJob#recordFailure}가 이 값으로 다음 상태를 정한다.
 *
 * <p>분류 <b>규칙</b>(어떤 예외가 어느 종류인가)은 외부 호출의 예외 타입을 아는 애플리케이션 계층이 판단하고
 * (도메인은 Spring/HTTP를 모른다), 이 enum <b>값만</b> 도메인으로 넘긴다:
 * <ul>
 *   <li>{@link #RETRYABLE}: timeout·5xx·429 — 원인이 사라지면 성공할 수 있다 → 백오프 후 재시도.</li>
 *   <li>{@link #PERMANENT}: 4xx·파싱 실패 — 다시 불러도 같은 결과다 → 즉시 영구 실패(사람이 본다).</li>
 * </ul>
 */
public enum JobFailureType {

	/** 일시적 실패(timeout·5xx·429) — 백오프 후 재시도. 최대 시도를 넘기면 영구 실패로 승격된다. */
	RETRYABLE,

	/** 항구적 실패(4xx·파싱 실패) — 재시도해도 소용없으므로 즉시 {@link JobStatus#FAILED_PERMANENT}. */
	PERMANENT
}
