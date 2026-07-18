package modi.backend.domain.exhibition.catalog;

import java.util.Optional;

import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * 전시 카테고리 enum. (03_전시.md RULE: 카테고리 — {@code ⚠가정} 실제 값 팀 확정 전 임시 셋)
 * 외부 API는 realmName(미술/전시 등 상위 분야)만 주므로 {@link #fromRealmName}은 휴리스틱 매핑이다.
 */
public enum ExhibitionCategory {
	PAINTING, PHOTO, MEDIA, SCULPTURE, DESIGN, CRAFT, ARCHITECTURE, PERFORMANCE, ETC;

	/** 클라이언트가 보낸 카테고리 코드 → enum. 미정의 코드는 {@link ErrorType#INVALID_INPUT}. */
	public static ExhibitionCategory from(String code) {
		try {
			return valueOf(code.trim().toUpperCase());
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new CoreException(ErrorType.INVALID_INPUT, "정의되지 않은 카테고리 코드: " + code);
		}
	}

	/**
	 * 외부 전시 API의 realmName(분야 텍스트) → 카테고리 휴리스틱 매핑. 매핑 실패/공백은 {@link #ETC}.
	 * realmName 세부 값과 본 카테고리 셋이 1:1이 아니므로 근사치다 — 04_전시_구현.md 오픈 질문 참고.
	 */
	public static ExhibitionCategory fromRealmName(String realmName) {
		String text = Optional.ofNullable(realmName).map(String::trim).orElse("");
		if (text.contains("사진")) {
			return PHOTO;
		}
		if (text.contains("조각")) {
			return SCULPTURE;
		}
		if (text.contains("미디어") || text.contains("영상")) {
			return MEDIA;
		}
		if (text.contains("미술") || text.contains("회화") || text.contains("전시")) {
			return PAINTING;
		}
		return ETC;
	}
}
