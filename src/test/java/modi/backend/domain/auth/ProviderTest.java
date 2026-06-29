package modi.backend.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.support.error.CoreException;

class ProviderTest {

	@Test
	@DisplayName("화이트리스트 코드는 enum으로 변환된다")
	void from_지원코드_변환() {
		assertThat(Provider.from("kakao")).isEqualTo(Provider.KAKAO);
		assertThat(Provider.from("google")).isEqualTo(Provider.GOOGLE);
	}

	@Test
	@DisplayName("미지원 코드는 UNSUPPORTED_PROVIDER로 거부된다")
	void from_미지원코드_예외() {
		assertThatThrownBy(() -> Provider.from("naver"))
				.isInstanceOf(CoreException.class)
				.extracting(e -> ((CoreException) e).errorCode())
				.isEqualTo(AuthErrorCode.UNSUPPORTED_PROVIDER);
	}

	@Test
	@DisplayName("code()는 경계에서 쓰는 소문자 코드를 돌려준다")
	void code_소문자() {
		assertThat(Provider.KAKAO.code()).isEqualTo("kakao");
		assertThat(Provider.GOOGLE.code()).isEqualTo("google");
	}
}
