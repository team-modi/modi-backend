package modi.backend.application.exhibition.ingest;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import modi.backend.domain.exhibition.enrichment.JobFailureType;

/**
 * 외부 호출 예외를 재시도 분류({@link JobFailureType})로 옮기는 규칙 — <b>애플리케이션 계층</b>의 몫이다.
 *
 * <p>도메인({@link modi.backend.domain.exhibition.enrichment.EnrichmentJob})은 Spring/HTTP를 몰라야 하므로 예외 타입을 알 수
 * 없다. 그래서 "어떤 예외가 재시도 가능한가"는 여기서 판정하고, 결과 <b>값</b>만 도메인으로 넘긴다.
 *
 * <ul>
 *   <li>timeout·5xx·429 → {@link JobFailureType#RETRYABLE}(원인이 사라지면 성공 가능).</li>
 *   <li>4xx(429 제외)·파싱 실패 → {@link JobFailureType#PERMANENT}(다시 불러도 같은 결과).</li>
 *   <li>그 외(원인 불명) → RETRYABLE(안전 기본값 — 잘못 영구 폐기하느니 재시도하고, 시도를 소진하면 어차피 PERMANENT로 승격).</li>
 * </ul>
 */
final class JobFailures {

	private JobFailures() {
	}

	static JobFailureType classify(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause()) {
			if (t instanceof WebClientResponseException web) {
				int status = web.getStatusCode().value();
				boolean permanent = status >= 400 && status < 500 && status != 429;
				return permanent ? JobFailureType.PERMANENT : JobFailureType.RETRYABLE;
			}
			if (isParseFailure(t)) {
				return JobFailureType.PERMANENT;
			}
		}
		return JobFailureType.RETRYABLE;
	}

	/** 파싱·형식 오류 — 재시도해도 같은 입력이면 같은 실패다. */
	private static boolean isParseFailure(Throwable t) {
		return t instanceof com.fasterxml.jackson.core.JsonProcessingException
				|| t instanceof NumberFormatException
				|| t instanceof IllegalArgumentException;
	}

	/** 예외 메시지를 last_error에 남길 짧은 문자열로. */
	static String describe(Throwable error) {
		if (error == null) {
			return null;
		}
		String message = error.getMessage();
		return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
	}
}
