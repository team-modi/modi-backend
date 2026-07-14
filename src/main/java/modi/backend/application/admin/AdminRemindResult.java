package modi.backend.application.admin;

/**
 * 관리자 리마인드 설정 유스케이스 출력. Facade→Controller 사이 DTO(용도명 중첩 record).
 */
public final class AdminRemindResult {

	private AdminRemindResult() {
	}

	/**
	 * 현재 소환 대기 시간.
	 *
	 * @param seconds 대기 시간(초)
	 * @param label   사람이 읽는 표기(예: "3초", "1분", "7일")
	 */
	public record EligibleAfter(long seconds, String label) {
	}
}
