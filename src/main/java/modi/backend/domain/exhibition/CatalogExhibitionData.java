package modi.backend.domain.exhibition;

import java.time.LocalDate;

/**
 * 외부 전시 API 한 건의 정규화된 수집 데이터(도메인 포트 출력).
 * 인프라(외부 응답 파싱)와 애플리케이션(Exhibition 매핑) 사이의 경계 DTO — HTTP·XML/JSON 세부를 도메인에 노출하지 않는다.
 * 결측이 잦은 원천 특성상 대부분 필드가 nullable이다(공공데이터 리뷰: 좌표·썸네일·가격 빈 값 빈번).
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
		String areaText) {

	/** 원천 식별자·제목이 없는 행은 적재 불가 — 유효한 수집 데이터만 통과시킨다. */
	public boolean isPersistable() {
		return externalId != null && !externalId.isBlank() && title != null && !title.isBlank();
	}
}
