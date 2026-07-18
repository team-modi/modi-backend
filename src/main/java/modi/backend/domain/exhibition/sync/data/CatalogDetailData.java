package modi.backend.domain.exhibition.sync.data;

import modi.backend.domain.exhibition.sync.data.PlaceHoursData;

/**
 * detail2 지연 수집 필드. 목록엔 없고 상세 진입 시 채운다. 결측 잦아 전부 nullable.
 *
 * @param payload detail2 응답 아이템의 매핑 JSON(도메인 변환 이전 값) — 벤더층({@code culture_detail_response}) 적재용.
 *                도메인은 해석하지 않는 불투명 값이다({@link PlaceHoursData#rawJson()}과 같은 역할).
 *                특히 {@code description}의 원천은 워드프레스 블록/HTML이라, 평문 추출 규칙이 바뀌면 이 원문에서 재추출한다.
 */
public record CatalogDetailData(String price, String description, String detailUrl, String phone,
		String imgUrl, String placeUrl, String placeAddr, String placeSeq, String payload) {
}
