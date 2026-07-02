package modi.backend.domain.exhibition;

/** detail2 지연 수집 필드. 목록엔 없고 상세 진입 시 채운다. 결측 잦아 전부 nullable. */
public record CatalogDetailData(String price, String description, String detailUrl, String phone,
		String imgUrl, String placeUrl, String placeAddr, String placeSeq) {
}
