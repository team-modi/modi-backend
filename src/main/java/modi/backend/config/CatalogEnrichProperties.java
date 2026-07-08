package modi.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공데이터(CATALOG) 보강(enrich) 설정. {@code app.exhibition.enrich.*} 바인딩.
 * <p>
 * 장르는 미분류 행을 배치로(=Gemini 1콜/배치) 상한만큼 반복해 전량 백필하고, 가격 등 상세2 필드는 상세 API로 백필한다.
 * 둘 다 대상이 "미처리 행"이라 멱등 — 반복 실행돼도 신규 행만 처리해 AI 호출·외부 호출을 아낀다(같은 퀄리티, 최소 비용).
 *
 * @param genreBatchSize        장르 배치 1회(=AI 1콜)에서 분류하는 전시 수.
 * @param genreMaxBatchesPerRun 한 번의 보강 실행에서 도는 최대 배치 수(폭주 방지 상한, 이 안에서 미분류 소진 시 조기 종료).
 * @param detailMaxPerRun       한 번의 보강 실행에서 상세를 채우는 최대 전시 수(외부 상세 API 호출 상한).
 */
@ConfigurationProperties(prefix = "app.exhibition.enrich")
public record CatalogEnrichProperties(Integer genreBatchSize, Integer genreMaxBatchesPerRun,
		Integer detailMaxPerRun) {

	public CatalogEnrichProperties {
		if (genreBatchSize == null || genreBatchSize <= 0) {
			genreBatchSize = 40;
		}
		if (genreMaxBatchesPerRun == null || genreMaxBatchesPerRun <= 0) {
			genreMaxBatchesPerRun = 20;
		}
		if (detailMaxPerRun == null || detailMaxPerRun <= 0) {
			detailMaxPerRun = 150;
		}
	}
}
