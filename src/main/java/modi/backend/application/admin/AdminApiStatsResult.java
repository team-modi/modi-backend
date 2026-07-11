package modi.backend.application.admin;

import java.util.List;

/**
 * API별 통계(엔드포인트 호출수·에러·지연) 유스케이스 출력. Micrometer http.server.requests에서 집계한다.
 */
public final class AdminApiStatsResult {

	private AdminApiStatsResult() {
	}

	public record ApiStats(long totalCount, long totalErrors, List<Endpoint> endpoints) {
	}

	public record Endpoint(String method, String uri, long count, long errorCount, double avgMs, double maxMs) {
	}
}
