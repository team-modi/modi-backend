package modi.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgeGroupTest {

	@Test
	@DisplayName("카카오 age_range → 연령대 매핑(정상 구간)")
	void fromKakaoAgeRange_매핑() {
		assertThat(AgeGroup.fromKakaoAgeRange("10~14")).isEqualTo(AgeGroup.TEENS);
		assertThat(AgeGroup.fromKakaoAgeRange("15~19")).isEqualTo(AgeGroup.TEENS);
		assertThat(AgeGroup.fromKakaoAgeRange("20~29")).isEqualTo(AgeGroup.TWENTIES);
		assertThat(AgeGroup.fromKakaoAgeRange("30~39")).isEqualTo(AgeGroup.THIRTIES);
		assertThat(AgeGroup.fromKakaoAgeRange("40~49")).isEqualTo(AgeGroup.FORTIES);
		assertThat(AgeGroup.fromKakaoAgeRange("50~59")).isEqualTo(AgeGroup.FIFTIES_PLUS);
		assertThat(AgeGroup.fromKakaoAgeRange("60~69")).isEqualTo(AgeGroup.FIFTIES_PLUS);
		assertThat(AgeGroup.fromKakaoAgeRange("90~")).isEqualTo(AgeGroup.FIFTIES_PLUS);
	}

	@Test
	@DisplayName("미동의(null)·10대 미만·알 수 없는 값 → UNSPECIFIED")
	void fromKakaoAgeRange_기타_UNSPECIFIED() {
		assertThat(AgeGroup.fromKakaoAgeRange(null)).isEqualTo(AgeGroup.UNSPECIFIED);
		assertThat(AgeGroup.fromKakaoAgeRange("1~9")).isEqualTo(AgeGroup.UNSPECIFIED);
		assertThat(AgeGroup.fromKakaoAgeRange("모름")).isEqualTo(AgeGroup.UNSPECIFIED);
	}
}
