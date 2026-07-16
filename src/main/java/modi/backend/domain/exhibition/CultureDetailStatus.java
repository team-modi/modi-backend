package modi.backend.domain.exhibition;

/**
 * 상세(detail2) 수집 상태기계 — {@code culture_detail_response.status}.
 *
 * <p>이 enum이 푸는 문제: 현행은 {@code applyDetail()}(상세를 채움)과 {@code markDetailChecked()}(원천에 상세가 없음)가
 * <b>둘 다 {@code exhibitions.detail_synced_at}만 찍어</b>, "채웠다"와 "원천에 없다"가 구분되지 않는다. 단일 시각 컬럼은
 * "언제 시도했나"만 말할 수 있고 "무슨 일이 있었나"를 말하지 못한다.
 *
 * <p>상태기계가 벤더 테이블에 있는 것은 의도다 — 상세는 단일 원천이라 그 자리가 곧 정준이다
 * (영업시간은 성공/미발견이 벤더 무관 개념이라 정준층 {@code place_hours}에 둔다. 이 비대칭은 규칙의 결과다).
 */
public enum CultureDetailStatus {

	/**
	 * 아직 시도하지 않음. 이관 3단계(쓰기 이중화)에서는 <b>기록되지 않는다</b> — 지금은 실제로 detail2를 부른 뒤에만
	 * 행을 남기기 때문이다. 대상 선별이 이 테이블로 넘어오는 단계(목록 적재 시점에 행을 미리 만드는 시점)에서 쓰인다.
	 */
	PENDING,

	/** 원천이 상세를 줬다. */
	SUCCEEDED,

	/** 원천에 상세가 없다(빈 응답) — 재조회해도 소용없다. 현행이 SUCCEEDED와 구분하지 못하던 바로 그 상태다. */
	NO_DATA,

	/** 일시 실패(전송 오류 등) — 재시도 대상. */
	FAILED,

	/**
	 * 재시도 소진. 이관 3단계에서는 <b>기록되지 않는다</b> — 최대 시도 횟수와 백오프는 이 테이블이 대상 선별을
	 * 맡는 단계에서 정할 정책이고, 아무도 읽지 않는 지금 값을 지어내면 나중에 그 값을 근거로 오판하게 된다.
	 */
	EXHAUSTED;

	/** 더 시도해볼 여지가 있는 상태인가(재시도 선별용 — 선별 전환 단계에서 쓴다). */
	public boolean isRetryable() {
		return this == PENDING || this == FAILED;
	}
}
