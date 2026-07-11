package modi.backend.application.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.config.AdminProperties;
import modi.backend.support.error.CoreException;

/** 관리자 로그인 검증 — 이메일+비번 성공 시 정규화 이메일, 어느 하나 틀리면 401 단위 검증. */
class AdminAuthFacadeTest {

	private final AdminAuthFacade facade = new AdminAuthFacade(new AdminProperties(List.of("a@x.com"), "pw!"));

	@Test
	@DisplayName("화이트리스트 이메일 + 정확한 비번이면 정규화된 이메일 반환")
	void 성공() {
		assertThat(facade.authenticate(" A@X.com ", "pw!")).isEqualTo("a@x.com");
	}

	@Test
	@DisplayName("이메일 불일치 또는 비번 오류면 401")
	void 실패() {
		assertThatThrownBy(() -> facade.authenticate("b@x.com", "pw!")).isInstanceOf(CoreException.class);
		assertThatThrownBy(() -> facade.authenticate("a@x.com", "wrong")).isInstanceOf(CoreException.class);
		assertThatThrownBy(() -> facade.authenticate(null, null)).isInstanceOf(CoreException.class);
	}
}
