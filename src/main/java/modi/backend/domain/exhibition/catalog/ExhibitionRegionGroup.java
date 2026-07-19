package modi.backend.domain.exhibition.catalog;

import java.util.Arrays;
import java.util.List;

/**
 * 전시 지역 필터 그룹(디자인 시안 02_전시탐색_필터의 병합 칩 단위).
 * 클라이언트 필터 칩 1개 = 그룹 1개이며, 검색 시엔 {@link #regions()}로 펼친
 * {@link ExhibitionRegion} 목록(콤마 다중 region 파라미터)을 그대로 사용한다.
 * 그룹 구성이 바뀌면 서버만 수정하면 되도록 응답 DTO로 노출한다(GET /exhibitions/region-groups).
 */
public enum ExhibitionRegionGroup {

	SEOUL("서울", ExhibitionRegion.SEOUL),
	GYEONGGI_INCHEON("경기·인천", ExhibitionRegion.GYEONGGI, ExhibitionRegion.INCHEON),
	GANGWON("강원", ExhibitionRegion.GANGWON),
	DAEJEON_SEJONG_CHUNGCHEONG("대전·세종·충청",
		ExhibitionRegion.DAEJEON, ExhibitionRegion.SEJONG, ExhibitionRegion.CHUNGNAM, ExhibitionRegion.CHUNGBUK),
	GWANGJU_JEOLLA("광주·전라",
		ExhibitionRegion.GWANGJU, ExhibitionRegion.JEONNAM, ExhibitionRegion.JEONBUK),
	DAEGU_GYEONGBUK("대구·경북", ExhibitionRegion.DAEGU, ExhibitionRegion.GYEONGBUK),
	BUSAN_ULSAN_GYEONGNAM("부산·울산·경남",
		ExhibitionRegion.BUSAN, ExhibitionRegion.ULSAN, ExhibitionRegion.GYEONGNAM),
	JEJU("제주", ExhibitionRegion.JEJU),
	ETC("기타", ExhibitionRegion.ETC);

	private final String label;
	private final List<ExhibitionRegion> regions;

	ExhibitionRegionGroup(String label, ExhibitionRegion... regions) {
		this.label = label;
		this.regions = List.of(regions);
	}

	public String label() {
		return label;
	}

	public List<ExhibitionRegion> regions() {
		return regions;
	}

	/** 모든 {@link ExhibitionRegion}이 정확히 한 그룹에 속하는지는 도메인 테스트로 보장한다. */
	public static List<ExhibitionRegionGroup> all() {
		return Arrays.asList(values());
	}
}
