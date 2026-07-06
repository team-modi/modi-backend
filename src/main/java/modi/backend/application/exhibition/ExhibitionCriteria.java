package modi.backend.application.exhibition;

import java.time.LocalDate;

/**
 * 전시 유스케이스 입력 모음. (Request →[Controller] Criteria → Facade)
 * region·category는 원시 문자열로 받아 Facade에서 enum 변환한다(미정의 코드 → INVALID_INPUT).
 * requesterId는 인증(옵션)에서 채운다 — 비로그인이면 null.
 */
public final class ExhibitionCriteria {

	private ExhibitionCriteria() {
	}

	/**
	 * 목록/탐색 조회 입력. 페이지네이션은 별도 Pageable로 전달한다.
	 *
	 * @param sort 정렬 코드(latest|ending|popular). null·미정의 값이면 latest로 취급한다.
	 */
	public record Search(String keyword, LocalDate date, String region, String category, String sort,
			Long requesterId) {
	}

	/** 전시 상세 조회 입력. requesterId는 CUSTOM 접근 권한 판단용. */
	public record Detail(Long exhibitionId, Long requesterId) {
	}

	/** 개인 전시(CUSTOM) 등록 입력. ownerId는 인증에서 채운다. format은 전시 형태 코드(원시 문자열, Facade에서 enum 변환). */
	public record CustomCreate(Long ownerId, String title, String place, LocalDate startDate,
			LocalDate endDate, String region, String category, String format, String artist, String posterUrl) {
	}
}
