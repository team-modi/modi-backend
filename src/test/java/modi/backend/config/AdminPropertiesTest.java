package modi.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 관리자 로그인 자격 — 이메일 화이트리스트(대소문자·공백 무시) + 공용 비번(상수시간·미설정 거부) 단위 검증. */
class AdminPropertiesTest {

	@Test
	@DisplayName("이메일은 대소문자·앞뒤 공백 무시하고 화이트리스트 판정")
	void 이메일_판정() {
		AdminProperties props = new AdminProperties(List.of("A@x.com", " b@Y.com "), "pw");
		assertThat(props.isAllowedEmail("a@x.com")).isTrue();
		assertThat(props.isAllowedEmail(" A@X.com ")).isTrue();
		assertThat(props.isAllowedEmail("b@y.com")).isTrue();
		assertThat(props.isAllowedEmail("c@x.com")).isFalse();
		assertThat(props.isAllowedEmail(null)).isFalse();
	}

	@Test
	@DisplayName("비번은 정확히 일치해야 하고, 미설정(빈값)이면 항상 거부")
	void 비번_판정() {
		AdminProperties props = new AdminProperties(List.of("a@x.com"), "yeowunAdmin!");
		assertThat(props.passwordMatches("yeowunAdmin!")).isTrue();
		assertThat(props.passwordMatches("wrong")).isFalse();
		assertThat(props.passwordMatches(null)).isFalse();
		assertThat(new AdminProperties(List.of("a@x.com"), null).passwordMatches("")).isFalse();
	}

	@Test
	@DisplayName("이메일·비번 둘 다 있어야 configured")
	void 설정여부() {
		assertThat(new AdminProperties(List.of("a@x.com"), "pw").configured()).isTrue();
		assertThat(new AdminProperties(List.of(), "pw").configured()).isFalse();
		assertThat(new AdminProperties(List.of("a@x.com"), "").configured()).isFalse();
		assertThat(new AdminProperties(null, null).configured()).isFalse();
	}
}
