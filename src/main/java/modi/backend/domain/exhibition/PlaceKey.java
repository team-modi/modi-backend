package modi.backend.domain.exhibition;

/**
 * 전시장 식별 키(값 객체) — {@code exhibition_place.place_key}의 자연키를 만드는 <b>단 하나의 지점</b>이다(ADR-07).
 *
 * <p><b>키 = 정규화한 {@code place} 이름</b>이다(주소·gps 아님). 정규화는 앞뒤 공백 제거 + 연속 공백 1개화만 한다 —
 * 표기 흔들림(공백 중복)은 수렴시키되, prefix 병합("국립현대미술관"으로 뭉치기)이나 gps 병합 같은 <b>공격적 병합은
 * 하지 않는다</b>(ADR-07: 서울/과천/청주를 한 곳으로 잘못 합치거나, 본관과 영업시간이 다른 어린이박물관을 뭉치는 사고 방지).
 *
 * <p>왜 이름인가: {@code place}는 목록(전시 생성 시점)에서 오므로 생성 시점에 확정된다 → {@code exhibition_place_id}를
 * NOT NULL로 둘 수 있다(ADR-05·06). 주소({@code place_addr})는 상세가 도착해야 확정돼(늦음) 자연키로 못 쓴다.
 *
 * <p>산출 지점이 여기 한 곳으로 모여 있으므로, 향후 이름 표기 흔들림으로 인한 중복이 관측돼 gps 보조 병합을 넣더라도
 * {@link #of}만 바꾸면 전시·정준층·벤더층 전 경로에 동시에 적용된다.
 * (CLAUDE.md 규칙대로 영속화하지 않는다 — 엔티티는 원시값으로 저장하고 필요할 때 이 VO로 감싸 쓴다.)
 */
public record PlaceKey(String value) {

	/** 자연키를 만들 근거가 없는(이름 없는) 전시장을 위한 센티넬 — 백필/등록에서 이름이 비면 이 키로 수렴시킨다. */
	public static final String UNKNOWN = "(장소 미상)";

	/**
	 * 전시장 이름으로 자연키를 만든다. 앞뒤 공백 제거 + 연속 공백 1개화(ADR-07). 이름이 비면 {@link #UNKNOWN}.
	 * MySQL 백필(V31)의 {@code REGEXP_REPLACE(TRIM(name), '\\s+', ' ')}와 같은 규칙이어야 한다.
	 */
	public static String of(String name) {
		if (name == null) {
			return UNKNOWN;
		}
		String normalized = name.trim().replaceAll("\\s+", " ");
		return normalized.isEmpty() ? UNKNOWN : normalized;
	}
}
