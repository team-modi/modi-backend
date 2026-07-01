package modi.backend.domain.user;

import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * 연령대(통합기획서 안건5) — 정확한 나이 대신 받는 구간값.
 * 미입력·선택 안 함은 {@link #UNSPECIFIED}(기본값)로 저장한다.
 */
public enum AgeGroup {

	TEENS,
	TWENTIES,
	THIRTIES,
	FORTIES,
	FIFTIES_PLUS,
	UNSPECIFIED;

	/** 코드 검증 겸 변환. 미정의 코드면 {@link ErrorType#INVALID_INPUT}. */
	public static AgeGroup from(String code) {
		for (AgeGroup ageGroup : values()) {
			if (ageGroup.name().equals(code)) {
				return ageGroup;
			}
		}
		throw new CoreException(ErrorType.INVALID_INPUT, "지원하지 않는 연령대: " + code);
	}

	/**
	 * 카카오 {@code age_range}(예: {@code "20~29"})를 연령대로 매핑한다.
	 * 10대 미만·미동의·알 수 없는 값은 {@link #UNSPECIFIED}. 50대 이상은 모두 {@link #FIFTIES_PLUS}.
	 * (외부 코드→도메인 enum 변환은 실패 아님 — 알 수 없으면 예외 대신 UNSPECIFIED로 흡수)
	 */
	public static AgeGroup fromKakaoAgeRange(String ageRange) {
		if (ageRange == null) {
			return UNSPECIFIED;
		}
		return switch (ageRange) {
			case "10~14", "15~19" -> TEENS;
			case "20~29" -> TWENTIES;
			case "30~39" -> THIRTIES;
			case "40~49" -> FORTIES;
			case "50~59", "60~69", "70~79", "80~89", "90~" -> FIFTIES_PLUS;
			default -> UNSPECIFIED; // "1~9"(10대 미만) 등 enum 밖 구간
		};
	}

	/** 미지정(선택 안 함) 여부 — 응답에서 null 처리 판단용. */
	public boolean isUnspecified() {
		return this == UNSPECIFIED;
	}
}
