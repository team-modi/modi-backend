package modi.backend.domain.exhibition;

import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * 전시 형태(개인 전시 등록 시 선택). 개인전/단체전/기획전/아트페어.
 * 카테고리(회화·사진 등 매체)와는 별개 축이다. 경계(요청/저장)에서는 enum name으로 주고받는다.
 */
public enum ExhibitionFormat {

	SOLO,       // 개인전
	GROUP,      // 단체전(2인 이상)
	CURATED,    // 기획전
	ART_FAIR;   // 아트페어

	/** 클라이언트가 보낸 형태 코드 → enum. 미정의 코드는 {@link ErrorType#INVALID_INPUT}. */
	public static ExhibitionFormat from(String code) {
		try {
			return valueOf(code.trim().toUpperCase());
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new CoreException(ErrorType.INVALID_INPUT, "정의되지 않은 전시 형태 코드: " + code);
		}
	}
}
