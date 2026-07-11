package modi.backend.interfaces.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import modi.backend.application.admin.AdminApiStatsResult;
import modi.backend.application.admin.AdminMetricsResult;
import modi.backend.support.response.ApiResponse;

@Tag(name = "Admin - Metrics", description = "관리자 대시보드/집계 지표. 관리자 화이트리스트(app.admin.user-ids)만 접근.")
@SecurityRequirement(name = "bearerAuth")
public interface AdminMetricsV1ApiSpec {

	@Operation(summary = "대시보드 집계", description = "가입/기록/리마인드/전시 총계, 리마인드 전환율, 일자별 추이, 인기 감정 Top10.")
	ApiResponse<AdminMetricsResult.Dashboard> dashboard(
			@Parameter(description = "추이 조회 기간(일)", example = "30") int days);

	@Operation(summary = "API별 통계", description = "엔드포인트별 호출수·에러수·평균/최대 지연(Micrometer http.server.requests 기반).")
	ApiResponse<AdminApiStatsResult.ApiStats> apiStats();
}
