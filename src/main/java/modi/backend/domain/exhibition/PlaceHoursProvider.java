package modi.backend.domain.exhibition;

import java.util.Optional;

/**
 * 장소 영업시간 조회 포트(도메인 소유, 구현 무관). 애플리케이션은 이 인터페이스만 의존하고,
 * 실제 전략(구글 실호출/ mock)은 구현체가 담당한다 — {@code app.exhibition.place-hours.provider}로 교체(DIP).
 * <p>
 * 계약:
 * <ul>
 *   <li>장소를 찾았으면 결과를 반환한다(영업시간이 없어도 {@link WeeklyOpeningHours#empty()}로 채운 값을 반환).</li>
 *   <li>검색 결과가 없으면(장소 미발견) {@link Optional#empty()}.</li>
 *   <li>전송 오류(타임아웃·5xx 등)만 예외로 전파한다 — 상위(enricher)가 해당 장소만 스킵하고 다음 주기에 재시도한다.</li>
 * </ul>
 */
public interface PlaceHoursProvider {

	/**
	 * 장소명 + 주소로 영업시간을 조회한다.
	 *
	 * @param placeName 전시 장소명(예: "부산현대미술관"). 매칭 정확도를 위해 주소와 함께 질의에 사용.
	 * @param placeAddr 전시 주소(place_addr). 필수 입력.
	 */
	Optional<PlaceHoursData> fetch(String placeName, String placeAddr);
}
