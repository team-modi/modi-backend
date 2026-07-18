package modi.backend.application.exhibition.sync;

/**
 * DETAIL_SYNC 작업이 대상 전시를 만났을 때의 상태 — {@link DetailEnricher}가 처리 방향을 정한다.
 */
public enum DetailTargetState {

	/** 전시가 없다(신규 전시가 상세 실패로 아직 적재되지 않음) — 다음 카탈로그 sync가 목록으로 적재한다. */
	MISSING,

	/** 이미 상세까지 완성됐다(다른 경로가 채움) — 작업은 성공 처리한다(할 일 없음). */
	ALREADY_SYNCED,

	/** 전시는 있으나 상세 미완성 — 상세를 조회해 채운다. */
	NEEDS_DETAIL
}
