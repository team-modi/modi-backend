package modi.backend.ingestion.infra.culture;

import java.util.Arrays;
import java.util.Optional;

/**
 * 공공데이터포털(data.go.kr) OpenAPI 공통 표준 결과코드(응답 {@code <header><resultCode>}).
 * <p>
 * 이 원천은 <b>HTTP 200을 주면서도 실패 사유를 본문 resultCode에 담아</b> 보낸다(키 오류·한도 초과 등).
 * 그래서 성공 여부는 HTTP 상태가 아니라 이 코드로 판정해야 한다 — 정상은 {@link #NORMAL_SERVICE}({@code "00"}) 하나뿐이다.
 * <p>
 * "한눈에보는문화정보"만의 코드가 아니라 data.go.kr 전 서비스가 공유하는 표준이며(그래서 벤더 어휘 = infra 소유,
 * 우리 도메인 ErrorCode 아님), 여기서는 <b>사람이 읽을 라벨</b> 제공이 목적이다. 재시도 분류는 {@code OutboxFailures},
 * 감사 결과는 {@code ExternalApiOutcome}가 별도로 담당하므로 이 enum에 HTTP 상태·재시도 정책을 얹지 않는다.
 */
public enum CultureResultCode {

	NORMAL_SERVICE("00", "NORMAL_SERVICE", "정상"),
	APPLICATION_ERROR("01", "APPLICATION_ERROR", "제공기관 애플리케이션 에러"),
	DB_ERROR("02", "DB_ERROR", "제공기관 데이터베이스 에러"),
	NODATA_ERROR("03", "NODATA_ERROR", "데이터 없음"),
	HTTP_ERROR("04", "HTTP_ERROR", "제공기관 HTTP 에러"),
	SERVICETIMEOUT_ERROR("05", "SERVICETIMEOUT_ERROR", "제공기관 서비스 연결 실패(타임아웃)"),
	INVALID_REQUEST_PARAMETER_ERROR("10", "INVALID_REQUEST_PARAMETER_ERROR", "잘못된 요청 파라미터"),
	NO_MANDATORY_REQUEST_PARAMETERS_ERROR("11", "NO_MANDATORY_REQUEST_PARAMETERS_ERROR", "필수 요청 파라미터 없음"),
	NO_OPENAPI_SERVICE_ERROR("12", "NO_OPENAPI_SERVICE_ERROR", "해당 오픈API 서비스가 없거나 폐기됨"),
	SERVICE_ACCESS_DENIED_ERROR("20", "SERVICE_ACCESS_DENIED_ERROR", "서비스 접근 거부"),
	TEMPORARILY_DISABLE_THE_SERVICEKEY_ERROR("21", "TEMPORARILY_DISABLE_THE_SERVICEKEY_ERROR",
			"일시적으로 사용할 수 없는 서비스키"),
	LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR("22", "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR",
			"서비스 요청제한횟수 초과"),
	SERVICE_KEY_IS_NOT_REGISTERED_ERROR("30", "SERVICE_KEY_IS_NOT_REGISTERED_ERROR", "등록되지 않은 서비스키"),
	DEADLINE_HAS_EXPIRED_ERROR("31", "DEADLINE_HAS_EXPIRED_ERROR", "기한 만료된 서비스키"),
	UNREGISTERED_IP_ERROR("32", "UNREGISTERED_IP_ERROR", "등록되지 않은 도메인 또는 IP"),
	UNSIGNED_CALL_ERROR("33", "UNSIGNED_CALL_ERROR", "서명되지 않은 호출"),
	UNKNOWN_ERROR("99", "UNKNOWN_ERROR", "기타 에러");

	private final String code;
	private final String standardMessage;
	private final String description;

	CultureResultCode(String code, String standardMessage, String description) {
		this.code = code;
		this.standardMessage = standardMessage;
		this.description = description;
	}

	public String code() {
		return code;
	}

	public String standardMessage() {
		return standardMessage;
	}

	public String description() {
		return description;
	}

	/** 원문 코드 문자열을 표준 코드로. 표에 없는 값(오타·미래 코드·null)이면 비어 있음 — 호출부가 원본을 잃지 않게 판단한다. */
	public static Optional<CultureResultCode> find(String code) {
		if (code == null) {
			return Optional.empty();
		}
		String trimmed = code.trim();
		return Arrays.stream(values()).filter(rc -> rc.code.equals(trimmed)).findFirst();
	}

	/** {@code "00"}(NORMAL_SERVICE)만 성공. null·미정의 코드는 실패로 본다(방어적 — 성공은 오직 정상 코드일 때만). */
	public static boolean isSuccess(String code) {
		return find(code).filter(rc -> rc == NORMAL_SERVICE).isPresent();
	}

	/**
	 * 코드를 로그·에러 메시지용 사람이 읽는 라벨로. 예: {@code "22 LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR(서비스 요청제한횟수 초과)"}.
	 * 표에 없는 코드는 원본을 그대로 실어 정보 손실을 막는다(운영에서 미문서화 코드가 와도 원값을 볼 수 있게).
	 */
	public static String describe(String code) {
		return find(code)
				.map(rc -> rc.code + " " + rc.standardMessage + "(" + rc.description + ")")
				.orElseGet(() -> code == null ? "null(코드 없음)" : code + "(정의되지 않은 코드)");
	}
}
