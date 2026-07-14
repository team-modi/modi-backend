package modi.backend.domain.exhibition;

/**
 * 한 장소의 영업시간 조회 결과(도메인 포트 출력). 인프라(구글/mock)와 애플리케이션 사이 경계 DTO —
 * HTTP·JSON 세부를 도메인에 노출하지 않는다.
 *
 * @param placeId       구글 place 리소스 id(mock은 합성값).
 * @param displayName   구글 장소명.
 * @param formattedAddress 구글 정규화 주소.
 * @param weeklyHours   요일별 영업시간(정보 없으면 {@link WeeklyOpeningHours#hasNoOpenDay()}).
 * @param rawJson       구글 응답 원본(regularOpeningHours) JSON — 스테이징 테이블에 그대로 적재.
 * @param source        출처 표기({@code GOOGLE} | {@code MOCK}).
 */
public record PlaceHoursData(String placeId, String displayName, String formattedAddress,
		WeeklyOpeningHours weeklyHours, String rawJson, String source) {
}
