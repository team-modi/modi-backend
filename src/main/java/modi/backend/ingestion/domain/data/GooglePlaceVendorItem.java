package modi.backend.ingestion.domain.data;

/**
 * 구글 Places(New) 응답의 벤더 어휘(ADR-13) — 스냅샷({@code google_place_snapshot}) 적재용.
 * 깊은 중첩(영업시간)은 구조 보존 JSON 문자열로 나른다.
 */
public record GooglePlaceVendorItem(
		String placeId,
		String displayName,
		String formattedAddress,
		String regularOpeningHoursJson) {
}
