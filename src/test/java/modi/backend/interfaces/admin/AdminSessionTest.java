package modi.backend.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.config.JwtProperties;

/** 관리자 세션 토큰 — 서명 왕복(발급→검증) + 변조·이종키·형식오류 거부 단위 검증. */
class AdminSessionTest {

	private final AdminSession session = new AdminSession(
			new JwtProperties("test-secret-please-change-min-32-bytes-aa", 900, 1_209_600));

	@Test
	@DisplayName("발급한 토큰은 원래 이메일로 검증된다")
	void 발급_검증_왕복() {
		String token = session.issue("a@x.com");
		assertThat(session.verify(token)).contains("a@x.com");
	}

	@Test
	@DisplayName("변조·형식오류·null·빈 토큰은 거부")
	void 변조_거부() {
		String token = session.issue("a@x.com");
		assertThat(session.verify(token + "x")).isEmpty(); // 서명 변조
		assertThat(session.verify("garbage")).isEmpty();
		assertThat(session.verify(null)).isEmpty();
		assertThat(session.verify("")).isEmpty();
	}

	@Test
	@DisplayName("다른 키로 서명된 토큰은 거부")
	void 이종키_거부() {
		String foreign = new AdminSession(new JwtProperties("another-secret-different-min-32-bytes-zz", 900, 1_209_600))
				.issue("a@x.com");
		assertThat(session.verify(foreign)).isEmpty();
	}
}
