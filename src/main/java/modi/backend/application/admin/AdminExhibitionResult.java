package modi.backend.application.admin;

/**
 * 관리자 전시 유지보수 작업 결과.
 */
public final class AdminExhibitionResult {

	private AdminExhibitionResult() {
	}

	/**
	 * 설명 재파싱 결과.
	 *
	 * @param scanned 설명이 있는 CATALOG 전시 수(검사 대상)
	 * @param updated 실제로 정리돼 갱신된 전시 수
	 */
	public record DescriptionReparse(int scanned, int updated) {
	}

	/**
	 * 전시 수정 결과.
	 *
	 * @param exhibitionId 수정한 전시
	 * @param changedFields 실제로 값이 바뀐 필드 수(= 이번 수정으로 남은 이력 행 수). 0이면 변경 없음(이력 없음).
	 */
	public record Edited(Long exhibitionId, int changedFields) {
	}
}
