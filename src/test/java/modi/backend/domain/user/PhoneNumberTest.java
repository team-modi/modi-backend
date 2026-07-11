package modi.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.support.error.CoreException;

/** PhoneNumber VO — 정규화(숫자만)·형식 규칙(01 시작 10~11자리) 순수 단위 검증. */
class PhoneNumberTest {

	@Test
	@DisplayName("하이픈·공백 섞인 입력을 숫자만으로 정규화한다")
	void 정규화() {
		assertThat(PhoneNumber.of("010-1234-5678").value()).isEqualTo("01012345678");
		assertThat(PhoneNumber.of("010 1234 5678").value()).isEqualTo("01012345678");
		assertThat(PhoneNumber.of("01112345678").value()).isEqualTo("01112345678");
		assertThat(PhoneNumber.of("016-123-4567").value()).isEqualTo("0161234567"); // 10자리도 허용
	}

	@Test
	@DisplayName("01로 시작하는 10~11자리가 아니면 INVALID_INPUT")
	void 형식오류() {
		assertThatThrownBy(() -> PhoneNumber.of("02-123-4567")).isInstanceOf(CoreException.class);
		assertThatThrownBy(() -> PhoneNumber.of("010-1234")).isInstanceOf(CoreException.class);
		assertThatThrownBy(() -> PhoneNumber.of("010-1234-56789")).isInstanceOf(CoreException.class); // 12자리
		assertThatThrownBy(() -> PhoneNumber.of("")).isInstanceOf(CoreException.class);
		assertThatThrownBy(() -> PhoneNumber.of(null)).isInstanceOf(CoreException.class);
	}
}
