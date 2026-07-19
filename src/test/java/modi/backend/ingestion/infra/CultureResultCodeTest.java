package modi.backend.ingestion.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.ingestion.infra.culture.CultureResultCode;

/**
 * {@link CultureResultCode} — data.go.kr 표준 결과코드 판정·라벨링 계약 검증.
 * "00만 성공", "미정의 코드는 원본 보존", "null 방어"가 핵심(운영 로그 판독성의 근거).
 */
class CultureResultCodeTest {

	@Test
	@DisplayName("00(NORMAL_SERVICE)만 성공이다")
	void isSuccess_onlyNormalService() {
		assertThat(CultureResultCode.isSuccess("00")).isTrue();
		assertThat(CultureResultCode.isSuccess("22")).isFalse();
		assertThat(CultureResultCode.isSuccess("99")).isFalse();
	}

	@Test
	@DisplayName("null·미정의 코드는 성공이 아니다(방어적)")
	void isSuccess_nullOrUnknown_false() {
		assertThat(CultureResultCode.isSuccess(null)).isFalse();
		assertThat(CultureResultCode.isSuccess("77")).isFalse();
	}

	@Test
	@DisplayName("앞뒤 공백이 있어도 코드를 해석한다")
	void find_trimsWhitespace() {
		assertThat(CultureResultCode.find(" 00 ")).contains(CultureResultCode.NORMAL_SERVICE);
	}

	@Test
	@DisplayName("describe는 코드+표준메시지+한글설명을 사람이 읽는 라벨로 만든다")
	void describe_knownCode_humanReadable() {
		assertThat(CultureResultCode.describe("22"))
				.isEqualTo("22 LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR(서비스 요청제한횟수 초과)");
	}

	@Test
	@DisplayName("describe는 표에 없는 코드도 원본을 그대로 실어 정보를 잃지 않는다")
	void describe_unknownOrNull_preservesRaw() {
		assertThat(CultureResultCode.describe("77")).isEqualTo("77(정의되지 않은 코드)");
		assertThat(CultureResultCode.describe(null)).isEqualTo("null(코드 없음)");
	}

	@Test
	@DisplayName("표준 코드 17개가 모두 고유한 code 값을 가진다(중복 등록 방지)")
	void codes_areUnique() {
		long distinct = java.util.Arrays.stream(CultureResultCode.values())
				.map(CultureResultCode::code).distinct().count();
		assertThat(distinct).isEqualTo(CultureResultCode.values().length);
	}
}
