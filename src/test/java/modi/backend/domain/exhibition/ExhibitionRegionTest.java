package modi.backend.domain.exhibition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExhibitionRegionTest {

	@Test
	@DisplayName("외부 API area 자유 텍스트 → 지역 enum 매핑")
	void 지역텍스트_매핑() {
		assertThat(ExhibitionRegion.fromAreaText("부산")).isEqualTo(ExhibitionRegion.BUSAN);
		assertThat(ExhibitionRegion.fromAreaText("경기")).isEqualTo(ExhibitionRegion.GYEONGGI);
		assertThat(ExhibitionRegion.fromAreaText("세종특별자치시")).isEqualTo(ExhibitionRegion.SEJONG);
		assertThat(ExhibitionRegion.fromAreaText("광주")).isEqualTo(ExhibitionRegion.ETC); // 칩에 없음
		assertThat(ExhibitionRegion.fromAreaText(null)).isEqualTo(ExhibitionRegion.ETC);
	}
}
