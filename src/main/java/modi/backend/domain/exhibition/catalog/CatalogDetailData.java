package modi.backend.domain.exhibition.catalog;

/**
 * detail2 지연 수집 필드(도메인 변환 후 값). 목록엔 없고 상세 진입 시 채운다. 결측 잦아 전부 nullable.
 * 벤더 원문은 수집(ingestion) 쪽 {@code CatalogVendorItem}이 따로 나른다(ADR-13) — 코어는 원문을 모른다.
 */
public record CatalogDetailData(String price, String description, String detailUrl, String phone,
		String imgUrl, String placeUrl, String placeAddr, String placeSeq) {
}
