package modi.backend.domain.exhibition.catalog;

/**
 * 전시 출처 구분. (03_전시.md 3.0 용어 정의)
 * CATALOG = 외부 공개 전시 API 수집 전시(공용), CUSTOM = 사용자가 직접 등록한 개인 전시.
 */
public enum ExhibitionType {
	CATALOG, CUSTOM
}
