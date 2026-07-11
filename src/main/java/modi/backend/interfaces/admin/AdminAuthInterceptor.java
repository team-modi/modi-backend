package modi.backend.interfaces.admin;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * `/api-admin/**` 게이트 — 로그인 시 발급된 {@code admin_session} 쿠키(또는 X-Admin-Session 헤더)를 검증한다.
 * 유효한 세션이 없으면 401. `/api-admin/v1/login`은 WebConfig에서 제외(그래야 로그인 가능).
 * 인터셉터(필터 아님)라 예외는 전역 예외 핸들러가 401로 매핑한다.
 * AdminSession은 ObjectProvider로 선택 주입 — @WebMvcTest 슬라이스(JwtProperties 없음)에서도 생성이 깨지지 않게.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

	private final ObjectProvider<AdminSession> adminSession;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		AdminSession session = adminSession.getIfAvailable();
		String token = resolveSessionToken(request);
		if (session == null || token == null || session.verify(token).isEmpty()) {
			throw new CoreException(ErrorType.UNAUTHORIZED);
		}
		return true;
	}

	private String resolveSessionToken(HttpServletRequest request) {
		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if (AdminSession.SESSION_COOKIE.equals(cookie.getName())
						&& cookie.getValue() != null && !cookie.getValue().isBlank()) {
					return cookie.getValue();
				}
			}
		}
		String header = request.getHeader("X-Admin-Session");
		return header != null && !header.isBlank() ? header : null;
	}
}
