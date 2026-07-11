package modi.backend.application.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

/**
 * API별 통계 — Spring Boot가 자동 수집하는 http.server.requests 타이머를 (method, uri)로 묶어 집계한다.
 * 별도 테이블 없이 인메모리 메트릭에서 읽는다(프로세스 재시작 시 리셋되는 건 관측 특성상 정상).
 */
@Service
@RequiredArgsConstructor
public class AdminApiStatsFacade {

	private final MeterRegistry meterRegistry;

	public AdminApiStatsResult.ApiStats stats() {
		Map<String, Agg> byEndpoint = new LinkedHashMap<>();
		for (Timer timer : meterRegistry.find("http.server.requests").timers()) {
			long count = timer.count();
			if (count == 0) {
				continue;
			}
			String method = tagOr(timer, "method", "-");
			String uri = tagOr(timer, "uri", "-");
			String outcome = tagOr(timer, "outcome", "");
			boolean isError = "CLIENT_ERROR".equals(outcome) || "SERVER_ERROR".equals(outcome);

			Agg agg = byEndpoint.computeIfAbsent(method + " " + uri, k -> new Agg(method, uri));
			agg.count += count;
			agg.totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
			agg.maxMs = Math.max(agg.maxMs, timer.max(TimeUnit.MILLISECONDS));
			if (isError) {
				agg.errorCount += count;
			}
		}

		long totalCount = 0;
		long totalErrors = 0;
		List<AdminApiStatsResult.Endpoint> endpoints = new ArrayList<>();
		for (Agg a : byEndpoint.values()) {
			totalCount += a.count;
			totalErrors += a.errorCount;
			double avg = a.count == 0 ? 0.0 : a.totalMs / a.count;
			endpoints.add(new AdminApiStatsResult.Endpoint(a.method, a.uri, a.count, a.errorCount, round(avg),
					round(a.maxMs)));
		}
		endpoints.sort(Comparator.comparingLong(AdminApiStatsResult.Endpoint::count).reversed());
		return new AdminApiStatsResult.ApiStats(totalCount, totalErrors, endpoints);
	}

	private static String tagOr(Timer timer, String key, String fallback) {
		String value = timer.getId().getTag(key);
		return value == null ? fallback : value;
	}

	private static double round(double value) {
		return Math.round(value * 10.0) / 10.0;
	}

	private static final class Agg {
		private final String method;
		private final String uri;
		private long count;
		private long errorCount;
		private double totalMs;
		private double maxMs;

		private Agg(String method, String uri) {
			this.method = method;
			this.uri = uri;
		}
	}
}
