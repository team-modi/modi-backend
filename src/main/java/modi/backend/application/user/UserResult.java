package modi.backend.application.user;

import java.util.List;

import modi.backend.domain.user.AgeGroup;
import modi.backend.domain.user.ResidenceRegion;
import modi.backend.domain.user.User;

/**
 * 사용자 유스케이스 출력 모음. (Facade는 Result까지만)
 * 미입력 값은 null로 내린다: ageGroup=UNSPECIFIED, residenceRegion/District 미입력 → null.
 */
public final class UserResult {

	private UserResult() {
	}

	/** 프로필 수정 결과(2.3.2 응답 = 2.3.1과 동일 shape + 온보딩 완료 여부). */
	public record Profile(Long userId, String provider, String nickname, boolean profileCompleted,
			String profileImageUrl, String ageGroup, Integer birthYear,
			String residenceRegion, String residenceDistrict) {

		public static Profile from(User user, String provider) {
			return new Profile(user.getId(), provider, user.getNickname(), user.isProfileCompleted(),
					user.getProfileImageUrl(), ageGroupCode(user.getAgeGroup()), user.getBirthYear(),
					regionCode(user.getResidenceRegion()), user.getResidenceDistrict());
		}
	}

	/** 프로필 조회 결과(2.3.1) — 프로필 + 취향 키워드 + 활동 통계. */
	public record Me(Long userId, String provider, String nickname, String profileImageUrl,
			String ageGroup, Integer birthYear, String residenceRegion, String residenceDistrict,
			List<String> tasteKeywords, Stats stats) {

		public static Me of(User user, String provider, List<String> tasteKeywords, Stats stats) {
			return new Me(user.getId(), provider, user.getNickname(), user.getProfileImageUrl(),
					ageGroupCode(user.getAgeGroup()), user.getBirthYear(), regionCode(user.getResidenceRegion()),
					user.getResidenceDistrict(), tasteKeywords, stats);
		}
	}

	/** 활동 통계(기록 수, 다녀온 전시 수=기록 남긴 서로 다른 전시 수, 북마크 수). 모두 실집계. */
	public record Stats(long recordCount, long exhibitionCount, long bookmarkCount) {
	}

	/** 알림 설정 조회·수정 결과(4.3·4.4) — 리마인드·공지 수신 여부. */
	public record NotificationSettings(boolean remindEnabled, boolean noticeEnabled) {

		public static NotificationSettings from(User user) {
			return new NotificationSettings(user.isRemindEnabled(), user.isNoticeEnabled());
		}
	}

	private static String ageGroupCode(AgeGroup ageGroup) {
		return ageGroup == null || ageGroup.isUnspecified() ? null : ageGroup.name();
	}

	private static String regionCode(ResidenceRegion region) {
		return region == null ? null : region.name();
	}
}
