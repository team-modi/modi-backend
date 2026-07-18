package modi.backend.domain.exhibition.catalog;

import java.util.Optional;

import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * 전시 지역 enum. (03_전시.md RULE: 지역 — {@code ⚠가정} 실제 값 팀 확정 전 임시 셋)
 * 클라이언트 코드({@link #from})와 외부 API의 자유 텍스트 area({@link #fromAreaText})를 각각 받아들인다.
 */
public enum ExhibitionRegion {
	SEOUL, GYEONGGI, INCHEON, GANGWON, DAEJEON, SEJONG, CHUNGNAM, CHUNGBUK, GWANGJU, JEONNAM, JEONBUK, DAEGU,
	GYEONGBUK, BUSAN, ULSAN, GYEONGNAM, JEJU, ETC;

	/** 클라이언트가 보낸 지역 코드 → enum. 미정의 코드는 {@link ErrorType#INVALID_INPUT}. */
	public static ExhibitionRegion from(String code) {
		try {
			return valueOf(code.trim().toUpperCase());
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new CoreException(ErrorType.INVALID_INPUT, "정의되지 않은 지역 코드: " + code);
		}
	}

	/**
	 * 외부 전시 API의 area 자유 텍스트(예: "서울", "경기") → enum. 매핑 실패/공백은 {@link #ETC}.
	 * (지역 매핑 규칙은 실제 데이터 확정 전 임시 — 04_전시_구현.md 오픈 질문 참고)
	 */
	public static ExhibitionRegion fromAreaText(String area) {
		String text = Optional.ofNullable(area).map(String::trim).orElse("");
		if (text.startsWith("서울")) {
			return SEOUL;
		}
		if (text.startsWith("경기")) {
			return GYEONGGI;
		}
		if (text.startsWith("인천")) {
			return INCHEON;
		}
		if (text.startsWith("강원")) {
			return GANGWON;
		}
		if (text.startsWith("대전")) {
			return DAEJEON;
		}
		if (text.startsWith("광주")) {
			return GWANGJU;
		}
		if (text.startsWith("대구")) {
			return DAEGU;
		}
		if (text.startsWith("경북") || text.startsWith("경상북")) {
			return GYEONGBUK;
		}
		if (text.startsWith("부산")) {
			return BUSAN;
		}
		if (text.startsWith("울산")) {
			return ULSAN;
		}
		if (text.startsWith("경남") || text.startsWith("경상남")) {
			return GYEONGNAM;
		}
		if (text.startsWith("세종")) {
			return SEJONG;
		}
		if (text.startsWith("전남") || text.startsWith("전라남")) {
			return JEONNAM;
		}
		if (text.startsWith("전북") || text.startsWith("전라북")) {
			return JEONBUK;
		}
		if (text.startsWith("제주")) {
			return JEJU;
		}
		if (text.startsWith("충남") || text.startsWith("충청남")) {
			return CHUNGNAM;
		}
		if (text.startsWith("충북") || text.startsWith("충청북")) {
			return CHUNGBUK;
		}
		return ETC;
	}
}
