package modi.backend.support.logging;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청마다 requestId를 MDC에 넣어 로그를 상관관계로 묶는다(한 요청의 로그를 추적 가능하게).
 * 외부에서 {@code X-Request-Id}를 주면 그대로 이어받고(게이트웨이/클라이언트 추적), 없으면 새로 발급한다.
 * 응답 헤더로도 돌려줘 클라이언트가 문의 시 그 값을 알려줄 수 있게 한다.
 *
 * <p>로깅 전용 필터다 — 인증은 {@code @Authentication} 리졸버가 담당한다(CLAUDE.md 규칙과 무관).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	private static final String HEADER = "X-Request-Id";
	private static final String MDC_KEY = "requestId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = request.getHeader(HEADER);
		if (requestId == null || requestId.isBlank()) {
			requestId = UUID.randomUUID().toString();
		}
		MDC.put(MDC_KEY, requestId);
		response.setHeader(HEADER, requestId);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}
}
