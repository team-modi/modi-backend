package modi.backend.ingestion.domain.data;

import modi.backend.domain.exhibition.hours.PlaceHoursData;

/**
 * 영업시간 조회 결과 한 벌 — 도메인 값({@link PlaceHoursData}, 파싱 후)과 벤더 원문({@link GooglePlaceVendorItem})을
 * 함께 나른다(ADR-13). mock 벤더는 원문이 없어 {@code vendor=null}이다(정준층에 provider=MOCK으로만 남는 게 정상).
 */
public record PlaceHoursFetch(PlaceHoursData data, GooglePlaceVendorItem vendor) {
}
