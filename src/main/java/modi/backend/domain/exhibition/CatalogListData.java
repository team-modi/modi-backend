package modi.backend.domain.exhibition;

import java.util.List;

/**
 * 목록 수집 1회의 결과(도메인 포트 출력) — 아이템들 + <b>그 수집이 원천을 다 가져왔는지</b>.
 *
 * <p><b>왜 List가 아니라 이 record인가</b>: {@code sync_run.total_count}·{@code truncated}를 채울 수 없기 때문이다.
 * 원천이 말한 총 건수는 <b>응답에만</b> 있고(어댑터 안에서 파싱되고 버려졌다), 절단 여부는 <b>페이지 순회를 한
 * 어댑터만</b> 안다. 포트가 아이템 목록만 돌려주면 호출부는 "다 가져왔나"에 답할 수 없다 —
 * 정준·감사 테이블의 컬럼을 채울 수 없으면 포트가 정보를 숨기고 있는 것이고, 그때 포트를 넓히는 건 설계의 요구다
 * (2단계 {@code GenreClassifier}, 4단계 {@code PlaceHoursProvider.vendor()}와 같은 판단).
 *
 * @param items      적재 가능한 수집 데이터.
 * @param totalCount 원천이 말한 총 건수. 호출 자체가 없었으면(인증키 미설정) null = <b>"모른다"</b>(0이 아니다).
 * @param truncated  원천에 더 있는데 상한({@code max-pages × num-of-rows})에 걸려 못 가져왔나.
 *                   현행이 감지하지 못하던 <b>조용한 절단</b>이 이 플래그다.
 */
public record CatalogListData(List<CatalogExhibitionData> items, Integer totalCount, boolean truncated) {

	/** 외부 호출을 하지 않았을 때(인증키 미설정) — 아무것도 모른다. */
	public static CatalogListData none() {
		return new CatalogListData(List.of(), null, false);
	}
}
