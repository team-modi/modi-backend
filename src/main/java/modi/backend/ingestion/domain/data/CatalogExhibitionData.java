package modi.backend.ingestion.domain.data;

import modi.backend.domain.exhibition.catalog.ExhibitionCategory;
import modi.backend.domain.exhibition.catalog.ExhibitionRegion;
import modi.backend.domain.exhibition.hours.PlaceHoursData;

import java.time.LocalDate;

/**
 * 외부 전시 API 한 건의 정규화된 수집 데이터(도메인 포트 출력).
 * 인프라(외부 응답 파싱)와 애플리케이션(Exhibition 매핑) 사이의 경계 DTO — HTTP·XML/JSON 세부를 도메인에 노출하지 않는다.
 * 결측이 잦은 원천 특성상 대부분 필드가 nullable이다(공공데이터 리뷰: 좌표·썸네일·가격 빈 값 빈번).
 *
 * @param vendorItem 벤더 원문 verbatim(스냅샷 적재용 — ADR-13)
 *                도메인은 이 문자열을 <b>해석하지 않는다</b>(불투명 값). XML 세부를 도메인에 노출하지 않는다는 이 DTO의
 *                계약과 어긋나지 않는 이유이며, {@link PlaceHoursData#rawJson()}이 이미 같은 역할을 같은 방식으로 한다.
 *                파싱에 실패했거나 조각을 특정할 수 없으면 null이다(적재를 건너뛴다).
 */
public record CatalogExhibitionData(
		String externalId,
		String title,
		String place,
		LocalDate startDate,
		LocalDate endDate,
		ExhibitionRegion region,
		ExhibitionCategory category,
		String posterUrl,
		String detailUrl,
		String serviceName,
		Double gpsX,
		Double gpsY,
		String sigungu,
		String realmName,
		String areaText,
		CatalogVendorItem vendorItem) {

	/** 원천 식별자·제목이 없는 행은 적재 불가 — 유효한 수집 데이터만 통과시킨다. */
	public boolean isPersistable() {
		return externalId != null && !externalId.isBlank() && title != null && !title.isBlank();
	}
}
