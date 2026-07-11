package modi.backend.interfaces.activitylog;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import modi.backend.application.activitylog.ActivityLogService;
import modi.backend.application.auth.AuthFacade;
import modi.backend.interfaces.auth.AccessTokens;

/**
 * 인증된 요청 1건을 활동 로그로 남긴다(관리자 콘솔 "실제 활동 기록"). 요청 처리 후 비동기 기록이라 응답 지연 없음.
 * 비인증(토큰 없음/무효) 요청은 사용자 활동이 아니므로 기록하지 않는다.
 * 등록 경로는 {@code /api/v1/**}(고객 API)만 — actuator·정적·admin은 제외(WebConfig).
 */
@Component
@RequiredArgsConstructor
public class ActivityLogInterceptor implements HandlerInterceptor {

	private static final String START_ATTR = "activityLogStartNanos";

	private final AuthFacade authFacade;
	// ObjectProvider로 선택 주입 — @WebMvcTest 슬라이스처럼 app 계층 빈이 없는 컨텍스트에서도 인터셉터가 안전히 생성된다.
	private final ObjectProvider<ActivityLogService> activityLogService;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		request.setAttribute(START_ATTR, System.nanoTime());
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		ActivityLogService service = activityLogService.getIfAvailable();
		if (service == null) {
			return; // 기록 서비스 없는 컨텍스트(슬라이스 테스트 등) — 기록 생략
		}
		Long userId = resolveUserId(request);
		if (userId == null) {
			return; // 인증된 요청만 기록
		}
		Object start = request.getAttribute(START_ATTR);
		long durationMs = start instanceof Long startNanos ? (System.nanoTime() - startNanos) / 1_000_000 : 0L;
		service.record(userId, request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
	}

	/** 토큰이 있고 유효하면 userId, 아니면 null(예외 없이 — 비인증 요청은 그냥 스킵). */
	private Long resolveUserId(HttpServletRequest request) {
		try {
			String token = AccessTokens.resolveOrNull(request);
			return token == null ? null : authFacade.requireAccess(token).userId();
		} catch (Exception e) {
			return null;
		}
	}
}
