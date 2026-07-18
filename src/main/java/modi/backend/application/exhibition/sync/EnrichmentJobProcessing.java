package modi.backend.application.exhibition.sync;

import java.time.LocalDateTime;

import org.springframework.dao.OptimisticLockingFailureException;

import modi.backend.domain.exhibition.enrichment.EnrichmentJob;
import modi.backend.domain.exhibition.enrichment.JobFailureType;

/**
 * 작업 상태 전이의 <b>낙관락 skip 규약</b>을 처리기들이 공유하는 지점.
 *
 * <p>스케줄러와 수동 트리거가 같은 작업을 동시에 집으면, 종료 전이 저장에서 한쪽만 이기고 다른 쪽은
 * {@link OptimisticLockingFailureException}으로 밀린다 = <b>다른 워커가 선점</b> = 정상 skip. 이 예외를 여기서
 * 흡수해 {@code false}(전이 못 함, 남이 처리)로 바꿔 준다 — 처리기는 boolean만 보면 된다.
 */
final class EnrichmentJobProcessing {

	private EnrichmentJobProcessing() {
	}

	/** 성공 전이. 다른 워커가 선점했으면 {@code false}. */
	static boolean succeed(EnrichmentJobFacade facade, EnrichmentJob job, LocalDateTime now) {
		try {
			facade.markSucceeded(job, now);
			return true;
		} catch (OptimisticLockingFailureException e) {
			return false;
		}
	}

	/** 실패 전이(백오프·최대 초과 승격은 도메인이 판단). 다른 워커가 선점했으면 {@code false}. */
	static boolean fail(EnrichmentJobFacade facade, EnrichmentJob job, JobFailureType failureType, String error,
			LocalDateTime now) {
		try {
			facade.markFailed(job, failureType, error, now);
			return true;
		} catch (OptimisticLockingFailureException e) {
			return false;
		}
	}
}
