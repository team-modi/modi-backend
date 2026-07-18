package modi.backend.domain.exhibition.sync.port;

import modi.backend.domain.exhibition.sync.data.CatalogDetailData;
import modi.backend.domain.exhibition.sync.data.CatalogListData;

import modi.backend.domain.exhibition.catalog.ExhibitionErrorCode;

import java.util.Optional;

/**
 * 외부 전시 API 수집 포트(도메인 소유). 구현은 infra(DIP) — 외부 HTTP·응답 포맷을 도메인에서 감춘다.
 * 외부 장애/통신 실패는 {@link ExhibitionErrorCode#EXTERNAL_API_UNAVAILABLE}로 변환해 던진다.
 */
public interface ExhibitionCatalogClient {

	/**
	 * 설정된 페이지 범위를 순회하며 적재 가능한 전시 수집 데이터를 모두 가져온다.
	 * 인증키 미설정 시 외부 호출 없이 {@link CatalogListData#none()}을 반환한다(데모는 시드 데이터로 동작).
	 *
	 * @return 수집 데이터 + 원천이 말한 총 건수·절단 여부(호출부가 sync_run에 남긴다)
	 */
	CatalogListData fetchAll();

	/**
	 * 단건 상세(detail2)를 지연 조회한다. 인증키 미설정/결과 없음은 빈 Optional.
	 *
	 * @param externalId 원천 seq
	 * @return 상세 확장 필드(없으면 빈 Optional)
	 */
	Optional<CatalogDetailData> fetchDetail(String externalId);
}
