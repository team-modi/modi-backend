package modi.backend.domain.exhibition;

/**
 * 통합 작업큐({@link EnrichmentJob})의 상태머신 — at-least-once 보장의 핵심.
 *
 * <pre>
 * PENDING ──▶ SUCCEEDED
 *        ╲
 *         ╲─▶ FAILED_RETRYABLE ──(next_attempt_at 도래 시 재선별)──▶ ...
 *          ╲                    ╲(최대 시도 초과)
 *           ╲──────────────────▶ FAILED_PERMANENT (4xx·파싱실패·시도초과 — 사람이 본다)
 * </pre>
 *
 * <p>상태가 <b>DB에 남으므로</b> 배포·재시작 후에도 PENDING/FAILED_RETRYABLE이 이어서 처리된다(재시작 생존).
 */
public enum JobStatus {

	/** 아직 시도하지 않음(또는 재활성화됨) — 즉시 선별 대상. */
	PENDING,

	/** 성공 — 종료 상태. */
	SUCCEEDED,

	/** 일시 실패(timeout·5xx·429 등) — {@code next_attempt_at} 백오프 후 재선별된다. "나중에 다시"가 기본값이다. */
	FAILED_RETRYABLE,

	/** 영구 실패(4xx·파싱 실패·최대 시도 초과) — 종료 상태. 운영자 조회 대상(수동 재시도로만 되살아난다). */
	FAILED_PERMANENT;

	/** 더 처리할 여지가 있는 상태인가(선별 후보). */
	public boolean isPending() {
		return this == PENDING || this == FAILED_RETRYABLE;
	}

	/** 더 손댈 일이 없는 종료 상태인가. */
	public boolean isTerminal() {
		return this == SUCCEEDED || this == FAILED_PERMANENT;
	}
}
