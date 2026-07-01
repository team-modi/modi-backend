package modi.backend.application.user;

/**
 * 사용자 유스케이스 입력 모음. (Request →[Controller] Criteria → Facade)
 */
public final class UserCriteria {

	private UserCriteria() {
	}

	/** 내 프로필 조회 입력. userId·provider는 인증(토큰)에서 채운다. */
	public record Me(Long userId, String provider) {
	}

	/**
	 * 프로필 수정 입력(부분 갱신). userId·provider는 인증에서, 나머지는 요청에서 채운다.
	 * ageGroup·residenceRegion은 원시 문자열로 받아 Facade에서 enum 변환(미정의 코드 → INVALID_INPUT).
	 */
	public record ProfileUpdate(Long userId, String provider, String nickname, String profileImageUrl,
			String ageGroup, String residenceRegion, String residenceDistrict) {
	}
}
