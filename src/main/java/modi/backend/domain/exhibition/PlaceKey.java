package modi.backend.domain.exhibition;

/**
 * 장소 식별 키(값 객체) — {@code exhibitions.place_key}·{@code place_hours.place_key}·{@code google_place_response.place_key}가
 * 공유하는 조인 키를 만드는 <b>단 하나의 지점</b>이다.
 *
 * <p><b>지금은 raw {@code place_addr} 그대로다</b>(2026-07-16 결정, 사용자 승인). 주소 정규화(같은 장소의 표기 흔들림을
 * 한 키로 수렴시키는 것)는 이번 이관의 범위 밖이다 — <b>구조 이관과 동작 변경을 한 번에 하면 회귀 원인을 분리할 수 없기
 * 때문</b>이다. 정규화를 넣는 순간 "장소가 몇 개인가"가 바뀌고, 그러면 영업시간 조회 건수·과금·표시값이 함께 흔들려
 * 무엇이 이관 탓인지 알 수 없게 된다.
 *
 * <p>이 VO의 존재 이유가 바로 그 다음 단계를 위한 것이다: 산출 지점이 여기 한 곳으로 모여 있으므로,
 * 정규화가 필요해지면 <b>{@link #of}만 바꾸면</b> 전시·정준층·벤더층 전 경로에 동시에 적용된다.
 * (CLAUDE.md 규칙대로 영속화하지 않는다 — 엔티티는 원시값으로 저장하고 필요할 때 이 VO로 감싸 쓴다.)
 */
public record PlaceKey(String value) {

	/**
	 * 주소로 장소 키를 만든다. 주소가 없으면(상세 미수집) {@code null} — 키를 만들 근거가 없다.
	 * 현재는 원문 유지가 규칙이라 공백만 다듬는다(저장 시 trim은 기존 {@code place_addr} 파싱과 동일한 처리다).
	 */
	public static String of(String placeAddr) {
		if (placeAddr == null || placeAddr.isBlank()) {
			return null;
		}
		return placeAddr.trim();
	}
}
