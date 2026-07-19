package modi.backend.domain.exhibition.hours;

import modi.backend.domain.exhibition.hours.PlaceHoursData;

/**
 * 영업시간을 준 벤더(정준층 — {@code place_hours.provider}).
 *
 * <p>이름이 {@code PlaceHoursProvider}가 아닌 이유는 그 이름이 이미 <b>조회 포트</b>(도메인 인터페이스)에
 * 쓰이고 있기 때문이다. 컬럼명은 ERD대로 {@code provider}를 유지한다.
 *
 * <p>{@code provider}를 남기는 이유: 벤더 전환이 이 프로젝트에선 가설이 아니라 이력이다(감상문 AI가 Gemini → Claude로
 * 실제 전환). 한국 서비스에서 구글 → 카카오/네이버 전환도 현실적 시나리오이고, 그때 <b>정준·도메인·읽기 경로는 불변</b>이며
 * 이 값만 늘어난다. 실무적으로 더 중요한 건 <b>mock으로 채워진 값과 실호출 결과를 구분</b>하는 것이다 —
 * 로컬·develop 기본이 mock이라 구분이 없으면 "진짜 영업시간"과 "가짜"가 DB에서 섞인다.
 */
public enum PlaceHoursVendor {

	GOOGLE,

	/** 아직 도입 전 — 카카오로 바꿔도 스키마 변경 0(값만 는다)임을 드러내려 미리 둔다(ERD 3장). */
	KAKAO,

	/** mock provider가 채운 값(로컬·develop 기본). 실호출 결과와 반드시 구분돼야 한다. */
	MOCK,

	/**
	 * 이관 전(V23 백필)에 이미 있던 값 — <b>출처 기록이 물리적으로 없다.</b>
	 * ⚠️ "조회 실패"가 아니다. 조회는 됐고 누가 줬는지를 모를 뿐이다({@link PlaceHoursStatus#SUCCEEDED}와 함께 쓰인다).
	 */
	UNKNOWN;

	/** 벤더 표기 문자열({@code PlaceHoursData.source})을 enum으로. 모르는 값은 UNKNOWN으로 떨어뜨린다(적재를 깨지 않는다). */
	public static PlaceHoursVendor from(String source) {
		if (source == null || source.isBlank()) {
			return UNKNOWN;
		}
		try {
			return valueOf(source.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return UNKNOWN;
		}
	}
}
