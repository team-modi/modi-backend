package modi.backend.interfaces.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import modi.backend.application.admin.AdminApiStatsFacade;
import modi.backend.application.admin.AdminApiStatsResult;
import modi.backend.application.admin.AdminMetricsFacade;
import modi.backend.application.admin.AdminMetricsResult;
import modi.backend.support.response.ApiResponse;

/**
 * 관리자 대시보드/API통계. `/api-admin/**` 는 {@code AdminAuthInterceptor}가 관리자만 통과시키므로
 * 컨트롤러는 조율만 한다. 내부 콘솔이라 application Result를 응답으로 직접 반환한다(별도 Response DTO 생략 — 의도된 단순화).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/metrics")
public class AdminMetricsV1Controller implements AdminMetricsV1ApiSpec {

	private final AdminMetricsFacade adminMetricsFacade;
	private final AdminApiStatsFacade adminApiStatsFacade;

	@Override
	@GetMapping("/dashboard")
	public ApiResponse<AdminMetricsResult.Dashboard> dashboard(@RequestParam(defaultValue = "30") int days) {
		return ApiResponse.success(adminMetricsFacade.dashboard(days));
	}

	@Override
	@GetMapping("/api-stats")
	public ApiResponse<AdminApiStatsResult.ApiStats> apiStats() {
		return ApiResponse.success(adminApiStatsFacade.stats());
	}
}
