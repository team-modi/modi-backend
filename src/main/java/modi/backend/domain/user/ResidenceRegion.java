package modi.backend.domain.user;

import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * 거주지역 시/도 단위(통합기획서 안건6) — 전국 17개 광역시·도.
 * 구/군 단위는 {@code residenceDistrict} 자유 문자열로 별도 저장(상세 주소는 미수집).
 */
public enum ResidenceRegion {

	SEOUL,
	BUSAN,
	DAEGU,
	INCHEON,
	GWANGJU,
	DAEJEON,
	ULSAN,
	SEJONG,
	GYEONGGI,
	GANGWON,
	CHUNGBUK,
	CHUNGNAM,
	JEONBUK,
	JEONNAM,
	GYEONGBUK,
	GYEONGNAM,
	JEJU;

	/** 코드 검증 겸 변환. 미정의 코드면 {@link ErrorType#INVALID_INPUT}. */
	public static ResidenceRegion from(String code) {
		for (ResidenceRegion region : values()) {
			if (region.name().equals(code)) {
				return region;
			}
		}
		throw new CoreException(ErrorType.INVALID_INPUT, "지원하지 않는 거주지역: " + code);
	}
}
