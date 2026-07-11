package modi.backend.application.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** API 통계 집계 — http.server.requests 타이머를 (method, uri)로 묶고 에러/지연을 합산하는 로직 단위 검증. */
class AdminApiStatsFacadeTest {

	@Test
	@DisplayName("같은 엔드포인트의 상태별 타이머를 합치고 에러수·평균/최대 지연을 집계한다")
	void 엔드포인트_집계() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		Timer success = Timer.builder("http.server.requests")
				.tag("method", "GET").tag("uri", "/api/v1/records").tag("status", "200").tag("outcome", "SUCCESS")
				.register(registry);
		success.record(Duration.ofMillis(10));
		success.record(Duration.ofMillis(20));
		success.record(Duration.ofMillis(30));
		Timer error = Timer.builder("http.server.requests")
				.tag("method", "GET").tag("uri", "/api/v1/records").tag("status", "500").tag("outcome", "SERVER_ERROR")
				.register(registry);
		error.record(Duration.ofMillis(5));

		AdminApiStatsResult.ApiStats stats = new AdminApiStatsFacade(registry).stats();

		assertThat(stats.totalCount()).isEqualTo(4);
		assertThat(stats.totalErrors()).isEqualTo(1);
		assertThat(stats.endpoints()).hasSize(1);
		AdminApiStatsResult.Endpoint ep = stats.endpoints().get(0);
		assertThat(ep.method()).isEqualTo("GET");
		assertThat(ep.uri()).isEqualTo("/api/v1/records");
		assertThat(ep.count()).isEqualTo(4);
		assertThat(ep.errorCount()).isEqualTo(1);
		assertThat(ep.maxMs()).isEqualTo(30.0);
		assertThat(ep.avgMs()).isEqualTo(16.3); // (10+20+30+5)/4 = 16.25 → 소수1자리 반올림
	}

	@Test
	@DisplayName("호출 많은 엔드포인트가 먼저 오도록 정렬한다")
	void 호출수_내림차순_정렬() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		Timer few = Timer.builder("http.server.requests")
				.tag("method", "GET").tag("uri", "/a").tag("status", "200").tag("outcome", "SUCCESS").register(registry);
		few.record(Duration.ofMillis(1));
		Timer many = Timer.builder("http.server.requests")
				.tag("method", "GET").tag("uri", "/b").tag("status", "200").tag("outcome", "SUCCESS").register(registry);
		many.record(Duration.ofMillis(1));
		many.record(Duration.ofMillis(1));

		AdminApiStatsResult.ApiStats stats = new AdminApiStatsFacade(registry).stats();

		assertThat(stats.endpoints().get(0).uri()).isEqualTo("/b");
		assertThat(stats.endpoints().get(1).uri()).isEqualTo("/a");
	}
}
