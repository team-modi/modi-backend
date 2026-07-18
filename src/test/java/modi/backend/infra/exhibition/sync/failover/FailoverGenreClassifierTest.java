package modi.backend.infra.exhibition.sync.failover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import modi.backend.domain.exhibition.genre.GenreProvider;
import modi.backend.domain.exhibition.sync.data.GenreClassification;
import modi.backend.domain.exhibition.sync.data.GenreResult;
import modi.backend.domain.exhibition.sync.port.GenreClassificationException;
import modi.backend.domain.exhibition.sync.port.GenreClassifier;

/**
 * FailoverGenreClassifier 단위 검증(ADR-11) — 1차 실패 시 2차 전환, 전 공급자 실패 시 예외,
 * 서킷 오픈 시 죽은 1차를 건너뛰고 2차 직행. resilience4j는 호출 내 계층만 담당한다(durable 재시도는 아웃박스).
 */
class FailoverGenreClassifierTest {

	private static final GenreClassification INPUT = new GenreClassification("전시", null, null, null, null, null);

	private static GenreResult gemini() {
		return GenreResult.ai("사진", GenreProvider.GEMINI, "gemini-2.5-flash");
	}

	private static GenreResult claude() {
		return GenreResult.ai("공예", GenreProvider.CLAUDE, "claude-haiku-4-5-20251001");
	}

	/** 재시도 없음(1회 시도) — 폴백 전환 로직만 또렷하게 보이는 구성. */
	private static Retry noRetry(String name) {
		return Retry.of(name, RetryConfig.custom().maxAttempts(1).build());
	}

	private static CircuitBreaker breaker(String name) {
		return CircuitBreaker.of(name, CircuitBreakerConfig.custom()
				.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
				.slidingWindowSize(2)
				.minimumNumberOfCalls(2)
				.failureRateThreshold(50)
				.waitDurationInOpenState(Duration.ofMinutes(1))
				.build());
	}

	private static GenreClassifier failing(AtomicInteger calls) {
		return input -> {
			calls.incrementAndGet();
			throw new GenreClassificationException("1차 장애");
		};
	}

	@Test
	@DisplayName("1차가 성공하면 2차를 호출하지 않는다")
	void 일차성공_이차미호출() {
		AtomicInteger secondaryCalls = new AtomicInteger();
		GenreClassifier secondary = input -> {
			secondaryCalls.incrementAndGet();
			return claude();
		};
		FailoverGenreClassifier chain = new FailoverGenreClassifier(input -> gemini(), secondary,
				noRetry("p"), noRetry("s"), breaker("p"), breaker("s"));

		GenreResult result = chain.classify(INPUT);

		assertThat(result.provider()).isEqualTo(GenreProvider.GEMINI);
		assertThat(secondaryCalls.get()).isZero();
	}

	@Test
	@DisplayName("1차 실패 시 2차로 전환하고, 계보엔 실제 분류자(CLAUDE)가 남는다")
	void 일차실패_이차전환() {
		FailoverGenreClassifier chain = new FailoverGenreClassifier(failing(new AtomicInteger()), input -> claude(),
				noRetry("p"), noRetry("s"), breaker("p"), breaker("s"));

		GenreResult result = chain.classify(INPUT);

		assertThat(result.provider()).isEqualTo(GenreProvider.CLAUDE);
	}

	@Test
	@DisplayName("전 공급자 실패면 분류 실패 예외를 던진다(1차 실패는 suppressed로 보존 — 아웃박스가 durable 재시도)")
	void 전공급자실패_예외() {
		GenreClassifier failingSecondary = input -> {
			throw new GenreClassificationException("2차도 장애");
		};
		FailoverGenreClassifier chain = new FailoverGenreClassifier(failing(new AtomicInteger()), failingSecondary,
				noRetry("p"), noRetry("s"), breaker("p"), breaker("s"));

		assertThatThrownBy(() -> chain.classify(INPUT))
				.isInstanceOf(GenreClassificationException.class)
				.hasMessageContaining("전 공급자 실패")
				.satisfies(e -> assertThat(e.getSuppressed()).isNotEmpty());
	}

	@Test
	@DisplayName("서킷 오픈 — 1차가 연속 실패해 차단되면 1차 호출 없이 2차로 직행한다(죽은 공급자에 타임아웃을 안 태움)")
	void 서킷오픈_이차직행() {
		AtomicInteger primaryCalls = new AtomicInteger();
		FailoverGenreClassifier chain = new FailoverGenreClassifier(failing(primaryCalls), input -> claude(),
				noRetry("p"), noRetry("s"), breaker("p"), breaker("s"));

		chain.classify(INPUT); // 실패 1 (2차 성공)
		chain.classify(INPUT); // 실패 2 → 윈도(2)·임계(50%) 충족 → 서킷 오픈
		int callsBeforeOpen = primaryCalls.get();
		chain.classify(INPUT); // 오픈 상태 — 1차 호출 자체가 차단된다

		assertThat(callsBeforeOpen).isEqualTo(2);
		assertThat(primaryCalls.get()).isEqualTo(2); // 차단 후 1차 미호출
	}

	@Test
	@DisplayName("Retry — 1차의 일시 오류가 호출 내 재시도로 풀리면 2차 없이 성공한다(호출 내 계층)")
	void 재시도로_일차회복() {
		AtomicInteger attempts = new AtomicInteger();
		GenreClassifier flaky = input -> {
			if (attempts.incrementAndGet() == 1) {
				throw new GenreClassificationException("일시 오류");
			}
			return gemini();
		};
		Retry twoAttempts = Retry.of("p", RetryConfig.custom()
				.maxAttempts(2).waitDuration(Duration.ofMillis(1)).build());
		AtomicInteger secondaryCalls = new AtomicInteger();
		GenreClassifier secondary = input -> {
			secondaryCalls.incrementAndGet();
			return claude();
		};
		FailoverGenreClassifier chain = new FailoverGenreClassifier(flaky, secondary,
				twoAttempts, noRetry("s"), breaker("p2"), breaker("s2"));

		GenreResult result = chain.classify(INPUT);

		assertThat(result.provider()).isEqualTo(GenreProvider.GEMINI);
		assertThat(attempts.get()).isEqualTo(2);
		assertThat(secondaryCalls.get()).isZero();
	}
}
