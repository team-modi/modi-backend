package modi.backend.application.exhibition;

/**
 * 영업시간 보강의 한 조회 단위(= 한 전시장). 전시장의 자연키가 정규화 이름(ADR-07)으로 정해지면서 <b>장소당 1콜</b>이
 * 전시장 행 하나에 직접 정렬된다(이관 3단계 — 기존의 placeAddr 그룹화가 exhibition_place로 대체됨).
 *
 * @param exhibitionPlaceId 대상 전시장 id(정준층 place_hours·벤더층 google_place_response의 조인 키).
 * @param placeName         전시장 이름(질의 정확도용).
 * @param placeAddr         조회 주소(구글 Places 질의 입력).
 */
public record PlaceHoursTarget(Long exhibitionPlaceId, String placeName, String placeAddr) {
}
