package modi.backend.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import modi.backend.config.JwtProperties;
import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/** 관리자 게이트 인터셉터 — admin_session 세션 쿠키 유무/유효성에 따른 통과·401 순수 단위 검증. */
class AdminAuthInterceptorTest {

	private final AdminSession adminSession = new AdminSession(
			new JwtProperties("test-secret-please-change-min-32-bytes-xy", 900, 1_209_600));
	private final AdminAuthInterceptor interceptor = new AdminAuthInterceptor(providerOf(adminSession));
	private final MockHttpServletResponse response = new MockHttpServletResponse();
	private final Object handler = new Object();

	@SuppressWarnings("unchecked")
	private static ObjectProvider<AdminSession> providerOf(AdminSession session) {
		ObjectProvider<AdminSession> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(session);
		return provider;
	}

	@Test
	@DisplayName("유효한 admin_session 쿠키면 통과")
	void 세션_통과() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie(AdminSession.SESSION_COOKIE, adminSession.issue("a@x.com")));

		assertThat(interceptor.preHandle(request, response, handler)).isTrue();
	}

	@Test
	@DisplayName("세션 쿠키가 없으면 401")
	void 세션없음() {
		assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest(), response, handler))
				.isInstanceOfSatisfying(CoreException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorType.UNAUTHORIZED));
	}

	@Test
	@DisplayName("변조된 세션 쿠키면 401")
	void 변조_거부() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie(AdminSession.SESSION_COOKIE, "tampered.token"));

		assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
				.isInstanceOf(CoreException.class);
	}
}
