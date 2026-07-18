package modi.backend.domain.exhibition.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지역 필터 그룹(디자인 병합 칩) 도메인 규칙 — 순수 단위 테스트.
 * 그룹 구성이 바뀌어도 "모든 지역이 정확히 한 그룹"이라는 불변식은 유지돼야 한다.
 */
class ExhibitionRegionGroupTest {

	@Test
	@DisplayName("모든 ExhibitionRegion은 정확히 한 그룹에 속한다(누락·중복 없음)")
	void 모든_지역이_정확히_한_그룹에_속한다() {
		List<ExhibitionRegion> grouped = ExhibitionRegionGroup.all().stream()
			.flatMap(g -> g.regions().stream())
			.toList();

		assertThat(grouped).hasSameSizeAs(ExhibitionRegion.values());
		assertThat(grouped).doesNotHaveDuplicates();
		assertThat(grouped).containsExactlyInAnyOrder(ExhibitionRegion.values());
	}

	@Test
	@DisplayName("그룹 칩 라벨·순서는 디자인 시안(02_전시탐색_필터)과 일치한다")
	void 그룹_라벨과_순서는_시안과_일치한다() {
		assertThat(ExhibitionRegionGroup.all()).extracting(ExhibitionRegionGroup::label)
			.containsExactly("서울", "경기·인천", "강원", "대전·세종·충청", "광주·전라",
				"대구·경북", "부산·울산·경남", "제주", "기타");
	}

	@Test
	@DisplayName("area 자유 텍스트에서 강원·대전·광주가 ETC가 아닌 전용 지역으로 매핑된다")
	void 신규_지역_텍스트_매핑() {
		assertThat(ExhibitionRegion.fromAreaText("강원")).isEqualTo(ExhibitionRegion.GANGWON);
		assertThat(ExhibitionRegion.fromAreaText("강원특별자치도")).isEqualTo(ExhibitionRegion.GANGWON);
		assertThat(ExhibitionRegion.fromAreaText("대전")).isEqualTo(ExhibitionRegion.DAEJEON);
		assertThat(ExhibitionRegion.fromAreaText("광주")).isEqualTo(ExhibitionRegion.GWANGJU);
		assertThat(ExhibitionRegion.fromAreaText("서울")).isEqualTo(ExhibitionRegion.SEOUL);
		assertThat(ExhibitionRegion.fromAreaText("이상한지역")).isEqualTo(ExhibitionRegion.ETC);
	}
}
