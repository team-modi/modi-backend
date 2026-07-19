package modi.backend.domain.exhibition.catalog;

import java.util.Optional;

/**
 * 전시 상세(detail2) 단건 조회 포트(코어 소유). 서빙의 지연 상세 충전과 수집(ingestion)의 상세 스텝이 함께 쓴다 —
 * 수집 전용 목록 수집은 이 포트를 확장한 ingestion 쪽 포트({@code ExhibitionCatalogClient})가 맡는다.
 * 구현은 ingestion infra(문화포털 어댑터) — DIP로 외부 HTTP·응답 포맷을 코어에서 감춘다.
 */
public interface ExhibitionDetailClient {

	/**
	 * 단건 상세(detail2)를 지연 조회한다. 인증키 미설정/결과 없음은 빈 Optional.
	 *
	 * @param externalId 원천 seq
	 * @return 상세 확장 필드(없으면 빈 Optional)
	 */
	Optional<CatalogDetailData> fetchDetail(String externalId);
}
