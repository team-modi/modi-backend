package modi.backend.application.exhibition.sync.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import modi.backend.domain.exhibition.sync.outbox.OutboxFailureType;
import modi.backend.support.error.CoreException;
import modi.backend.domain.exhibition.catalog.ExhibitionErrorCode;

/**
 * {@link OutboxFailures} 예외 → 재시도 분류 매핑 검증. RestClient 전환(ADR-09)으로 예외 계열이
 * {@code WebClientResponseException}에서 {@code RestClientResponseException}으로 바뀌었으므로,
 * "무엇이 RETRYABLE이고 무엇이 PERMANENT인가"라는 운영 의미론이 유지되는지를 여기서 박제한다.
 */
class OutboxFailuresTest {

	@Test
	@DisplayName("429는 원인이 사라지면 성공할 수 있으므로 RETRYABLE")
	void classify_tooManyRequests_retryable() {
		Exception error = httpError(HttpStatus.TOO_MANY_REQUESTS);

		assertThat(OutboxFailures.classify(error)).isEqualTo(OutboxFailureType.RETRYABLE);
	}

	@Test
	@DisplayName("5xx는 서버측 순간 장애일 수 있으므로 RETRYABLE")
	void classify_serverError_retryable() {
		Exception error = HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
				HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

		assertThat(OutboxFailures.classify(error)).isEqualTo(OutboxFailureType.RETRYABLE);
	}

	@Test
	@DisplayName("429를 제외한 4xx는 다시 불러도 같은 결과이므로 PERMANENT")
	void classify_clientError_permanent() {
		Exception error = httpError(HttpStatus.NOT_FOUND);

		assertThat(OutboxFailures.classify(error)).isEqualTo(OutboxFailureType.PERMANENT);
	}

	@Test
	@DisplayName("타임아웃(ResourceAccessException)은 상태코드 예외가 아니라 원인불명 기본값 RETRYABLE로 분류된다")
	void classify_timeout_retryable() {
		Exception error = new ResourceAccessException("I/O error", new java.net.http.HttpTimeoutException("read timed out"));

		assertThat(OutboxFailures.classify(error)).isEqualTo(OutboxFailureType.RETRYABLE);
	}

	@Test
	@DisplayName("CoreException으로 감싸져도 cause 체인을 타고 내려가 원래 상태코드로 분류한다")
	void classify_wrappedInCoreException_unwrapsCause() {
		Exception error = new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 전시 API 호출 실패",
				httpError(HttpStatus.BAD_REQUEST));

		assertThat(OutboxFailures.classify(error)).isEqualTo(OutboxFailureType.PERMANENT);
	}

	@Test
	@DisplayName("파싱 실패(JsonProcessingException)는 같은 입력이면 같은 실패이므로 PERMANENT")
	void classify_parseFailure_permanent() {
		Exception error = new com.fasterxml.jackson.core.JsonParseException(null, "unexpected token");

		assertThat(OutboxFailures.classify(error)).isEqualTo(OutboxFailureType.PERMANENT);
	}

	private static HttpClientErrorException httpError(HttpStatus status) {
		return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY, new byte[0],
				StandardCharsets.UTF_8);
	}
}
