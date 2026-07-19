package modi.backend.ingestion.domain.data;

import modi.backend.domain.exhibition.hours.WeeklyOpeningHours;

/**
 * 한 장소의 영업시간 조회 결과(도메인 포트 출력). 인프라(구글/mock)와 애플리케이션 사이 경계 DTO —
 * HTTP·JSON 세부를 도메인에 노출하지 않는다.
 *
 * @param weeklyHours 요일별 영업시간(정보 없으면 {@link WeeklyOpeningHours#hasNoOpenDay()}). 정준층 표시값의 재료다.
 * @param rawJson     벤더 응답 원본 — 벤더층({@code google_place_response})에 그대로 적재한다. 도메인은 해석하지 않는
 *                    불투명 값이다. 구글 Place 응답 <b>전체</b>(id·displayName·formattedAddress·regularOpeningHours)라,
 *                    V19가 별도 컬럼으로 갖고 있던 값들이 이 안에 보존된다.
 */
public record PlaceHoursData(WeeklyOpeningHours weeklyHours, String rawJson) {
}
