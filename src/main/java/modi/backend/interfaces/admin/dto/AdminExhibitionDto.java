package modi.backend.interfaces.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관리자 전시 수정 요청. 내부 운영용(@Hidden 컨트롤러). */
public final class AdminExhibitionDto {

	private AdminExhibitionDto() {
	}

	/**
	 * 전시 필드 수정(부분). 넘긴 필드만 갱신하고 나머지(null)는 건드리지 않는다 — 값을 비우려면 빈 문자열로 보낸다.
	 * 실제로 값이 바뀐 필드만 {@code exhibition_history}에 이력으로 남는다(같은 값 저장은 이력 없음).
	 */
	public record EditRequest(
			@Schema(description = "전시 제목(미지정 시 변경 안 함)") String title,
			@Schema(description = "장소명") String place,
			@Schema(description = "가격 표시 문자열") String price,
			@Schema(description = "전시 소개") String description) {
	}
}
