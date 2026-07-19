package modi.backend.ingestion.infra.failover;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import modi.backend.ingestion.domain.data.GenreClassification;
import modi.backend.ingestion.domain.data.GenreResult;
import modi.backend.ingestion.domain.port.GenreClassificationException;
import modi.backend.ingestion.domain.port.GenreClassifier;

/**
 * 장르 분류 <b>폴백 체인</b> — 1차(Gemini) 실패 시 2차(Claude)로 전환한다(ADR-11).
 *
 * <p>각 공급자 호출은 resilience4j로 감싼다:
 * <ul>
 *   <li><b>Retry</b> — 호출 내 즉시 재시도(일시 오류·429가 짧은 간격으로 풀리는 경우). 재시작을 넘는 durable
 *       재시도는 아웃박스 폴러의 몫이라 여기선 짧게만 시도한다(2계층 분리 — ADR-10).</li>
 *   <li><b>CircuitBreaker</b> — 연속 실패한 공급자를 잠시 차단해, 죽은 1차에 매번 타임아웃을 태우지 않고
 *       2차로 직행한다(회복되면 half-open으로 자동 복귀).</li>
 * </ul>
 *
 * <p>두 공급자가 모두 실패하면 {@link GenreClassificationException}을 던진다 — 아웃박스 메시지가 RETRYABLE로
 * 남고 draft는 분류될 때까지 승격을 대기한다(모든 AI 동시 장애 감수 — 사용자 확정).
 *
 * <p>빈이 아니라 {@code GenreConfig}가 조립하는 일반 클래스다 — 어떤 공급자를 어떤 순서로 묶을지는 구성의 결정이다.
 */
public class FailoverGenreClassifier implements GenreClassifier {

	private static final Logger log = LoggerFactory.getLogger(FailoverGenreClassifier.class);

	private final GenreClassifier primary;
	private final GenreClassifier secondary;
	private final Retry primaryRetry;
	private final Retry secondaryRetry;
	private final CircuitBreaker primaryBreaker;
	private final CircuitBreaker secondaryBreaker;

	public FailoverGenreClassifier(GenreClassifier primary, GenreClassifier secondary,
			Retry primaryRetry, Retry secondaryRetry,
			CircuitBreaker primaryBreaker, CircuitBreaker secondaryBreaker) {
		this.primary = primary;
		this.secondary = secondary;
		this.primaryRetry = primaryRetry;
		this.secondaryRetry = secondaryRetry;
		this.primaryBreaker = primaryBreaker;
		this.secondaryBreaker = secondaryBreaker;
	}

	@Override
	public GenreResult classify(GenreClassification input) {
		return callWithFailover(() -> primary.classify(input), () -> secondary.classify(input));
	}

	@Override
	public List<GenreResult> classifyAll(List<GenreClassification> inputs) {
		if (inputs == null || inputs.isEmpty()) {
			return List.of();
		}
		return callWithFailover(() -> primary.classifyAll(inputs), () -> secondary.classifyAll(inputs));
	}

	/** 1차(Retry+CB) → 실패·차단 시 2차(Retry+CB) → 둘 다 실패면 분류 실패 예외(아웃박스가 durable 재시도). */
	private <T> T callWithFailover(Supplier<T> primaryCall, Supplier<T> secondaryCall) {
		try {
			return decorate(primaryCall, primaryRetry, primaryBreaker).get();
		} catch (Exception primaryFailure) {
			log.warn("장르 1차 공급자 실패 — 2차로 전환: {}", primaryFailure.getMessage());
			try {
				return decorate(secondaryCall, secondaryRetry, secondaryBreaker).get();
			} catch (Exception secondaryFailure) {
				GenreClassificationException failure = new GenreClassificationException(
						"장르 분류 전 공급자 실패(1차: " + primaryFailure.getMessage()
								+ " / 2차: " + secondaryFailure.getMessage() + ")", secondaryFailure);
				failure.addSuppressed(primaryFailure);
				throw failure;
			}
		}
	}

	private static <T> Supplier<T> decorate(Supplier<T> call, Retry retry, CircuitBreaker breaker) {
		// 순서 유의: CircuitBreaker가 바깥 — 차단 중엔 Retry조차 돌지 않고 즉시 2차로 넘어간다.
		return CircuitBreaker.decorateSupplier(breaker, Retry.decorateSupplier(retry, call));
	}
}
