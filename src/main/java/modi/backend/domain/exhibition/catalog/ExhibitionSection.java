package modi.backend.domain.exhibition.catalog;

import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * 전시 탐색 섹션 필터(공통 코드 section 🆕). 목록 조회의 부가 조건축이다.
 * <ul>
 *   <li>{@link #ENDING_SOON} — 곧 끝나는 전시(종료일이 오늘~오늘+7일).</li>
 *   <li>{@link #OPENING_THIS_MONTH} — 이번 달(또는 period=week 시 이번 주) 시작 전시.</li>
 *   <li>{@link #FREE} — 무료 전시({@link Exhibition#isFree(String)} 규칙).</li>
 * </ul>
 * 경계에서는 케밥 코드(ending-soon 등)로 주고받는다.
 */
public enum ExhibitionSection {

	ENDING_SOON,
	OPENING_THIS_MONTH,
	FREE;

	/** 클라이언트가 보낸 섹션 코드(ending-soon 등) → enum. 미정의 코드는 {@link ErrorType#INVALID_INPUT}. */
	public static ExhibitionSection from(String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		return switch (code.trim().toLowerCase()) {
			case "ending-soon" -> ENDING_SOON;
			case "opening-this-month" -> OPENING_THIS_MONTH;
			case "free" -> FREE;
			default -> throw new CoreException(ErrorType.INVALID_INPUT, "정의되지 않은 섹션 코드: " + code);
		};
	}
}
