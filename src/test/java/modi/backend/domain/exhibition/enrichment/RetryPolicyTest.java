package modi.backend.domain.exhibition.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RetryPolicy 단위 검증 — 지수 백오프의 상한 캡과 시도 소진 판정, 생성 불변식. */
class RetryPolicyTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 0, 0);

	@Test
	@DisplayName("nextAttemptAt — 지수(base×2^(n-1))로 늘되 maxBackoff로 캡된다")
	void 지수백오프_캡() {
		RetryPolicy policy = new RetryPolicy(10, 60, 600);

		assertThat(policy.nextAttemptAt(1, NOW)).isEqualTo(NOW.plusSeconds(60)); // 60
		assertThat(policy.nextAttemptAt(2, NOW)).isEqualTo(NOW.plusSeconds(120)); // 120
		assertThat(policy.nextAttemptAt(3, NOW)).isEqualTo(NOW.plusSeconds(240)); // 240
		assertThat(policy.nextAttemptAt(4, NOW)).isEqualTo(NOW.plusSeconds(480)); // 480
		assertThat(policy.nextAttemptAt(5, NOW)).isEqualTo(NOW.plusSeconds(600)); // 960 → 캡 600
		assertThat(policy.nextAttemptAt(20, NOW)).isEqualTo(NOW.plusSeconds(600)); // 큰 시프트도 캡(오버플로 안전)
	}

	@Test
	@DisplayName("isExhausted — 시도 횟수가 maxAttempts 이상이면 소진")
	void 시도소진_판정() {
		RetryPolicy policy = new RetryPolicy(3, 60, 3600);

		assertThat(policy.isExhausted(2)).isFalse();
		assertThat(policy.isExhausted(3)).isTrue();
		assertThat(policy.isExhausted(4)).isTrue();
	}

	@Test
	@DisplayName("생성 불변식 — maxAttempts≥1, base≥1, max≥base")
	void 생성_불변식() {
		assertThatThrownBy(() -> new RetryPolicy(0, 60, 3600)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RetryPolicy(3, 0, 3600)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RetryPolicy(3, 600, 60)).isInstanceOf(IllegalArgumentException.class);
	}
}
