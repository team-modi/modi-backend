package modi.backend.domain.exhibition.enrichment;

/**
 * 통합 작업큐({@link EnrichmentJob})가 다루는 보강 작업의 종류.
 *
 * <p>현행은 상태머신이 축마다 흩어져 있다 — 상세는 {@code culture_detail_response}에 반쪽, 영업시간은
 * {@code place_hours}에 반쪽, 장르(AI)엔 아예 없다. 요구사항(at-least-once·수동 재시도·비용상한·재검증)을 축마다
 * 3벌 구현하는 대신, 진행 상태를 이 enum으로 갈라지는 <b>한 테이블</b>에 모은다(설계 §2).
 *
 * <p>{@code target_key}의 의미가 종류마다 다르다: 상세·장르는 전시 원천키({@code external_id}), 영업시간은
 * 장소키({@code place_key})다. 두 키 공간이 UK{@code (job_type, target_key)} 안에서 종류로 분리되므로 충돌하지 않는다.
 */
public enum JobType {

	/** 상세(detail2) 재시도 — {@code target_key = external_id}. 현행 최대 갭(쓰기만 되고 안 읽히던 재시도 상태)의 이관 대상. */
	DETAIL_SYNC,

	/** 장르(AI) 분류 — {@code target_key = external_id}. AI 장애 시 RETRYABLE로 남아 회복 후 자동 재분류("AI 최소 1회 무조건"). */
	GENRE_CLASSIFY,

	/** 영업시간 최초 조회 — {@code target_key = place_key}. 장소당 1회(유료). 큐 어휘의 일부로 정의된 종류다. */
	PLACE_HOURS_FETCH,

	/** 영업시간 재검증 — {@code target_key = place_key}. 새 전시가 기존 장소에 유입될 때 이벤트로 enqueue된다(설계 §4-1). */
	PLACE_HOURS_REFRESH
}
