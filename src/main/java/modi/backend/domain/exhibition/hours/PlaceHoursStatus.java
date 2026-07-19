package modi.backend.domain.exhibition.hours;

import modi.backend.domain.exhibition.hours.PlaceHoursData;

/**
 * 장소 영업시간 조회 상태(정준층 — {@code place_hours.status}).
 *
 * <p>이 enum이 푸는 문제: 현행은 {@code exhibitions.operating_hours}(표시값)와 {@code operating_hours_synced_at}
 * (조회 시각) 두 컬럼뿐이라, <b>"장소를 못 찾음"과 "장소는 찾았는데 영업시간 정보가 없음"이 구분되지 않는다</b> —
 * 둘 다 {@code operating_hours=null} + 시각만 남기기 때문이다. 그래서 "다시 찾아볼 가치가 있는 장소"를 골라낼 수 없다.
 *
 * <p>상태기계가 <b>정준층</b>에 있는 것은 의도다 — 성공/미발견은 벤더와 무관한 개념이라 카카오로 바꿔도 그대로다.
 * (상세 축은 상태기계가 벤더 테이블에 있다 — 단일 원천이라 그 자리가 곧 정준이기 때문. 이 비대칭은 규칙의 결과다.)
 */
public enum PlaceHoursStatus {

	/** 장소를 찾았고 영업시간도 받았다 — {@code formatted}에 표시값이 있다. */
	SUCCEEDED,

	/** 검색 결과 없음(장소 미발견). 주소가 틀렸거나 벤더가 모르는 장소다. */
	NOT_FOUND,

	/** 장소는 찾았지만 영업시간 정보가 없다. NOT_FOUND와 달리 <b>장소 매칭은 성공</b>했다는 뜻이다. */
	NO_HOURS,

	/** 전송 오류 등 일시 실패 — 재시도 대상. */
	FAILED;

	/**
	 * 조회 결과로 상태를 판정한다(도메인 규칙 — Facade가 판단하지 않는다).
	 * <p>
	 * {@code data}가 없으면 벤더가 장소를 못 찾은 것(NOT_FOUND)이고, 장소는 찾았는데 표시할 영업시간이 안 나오면
	 * NO_HOURS다. <b>현행 스키마가 이 둘을 구분하지 못한다</b>(둘 다 operating_hours=null) — 그 구분이 이 enum의 존재 이유다.
	 */
	public static PlaceHoursStatus of(PlaceHoursData data, String formatted) {
		if (data == null) {
			return NOT_FOUND;
		}
		return (formatted == null || formatted.isBlank()) ? NO_HOURS : SUCCEEDED;
	}

	/** 표시할 값이 나온 상태인가. */
	public boolean hasHours() {
		return this == SUCCEEDED;
	}
}
