package modi.backend.application.admin;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 관리자 사용자 조회 유스케이스 출력. 목록(집계 포함) + 상세(활동 기록 포함).
 */
public final class AdminUserResult {

	private AdminUserResult() {
	}

	public record UserListItem(
			Long userId,
			String nickname,
			String name,
			ZonedDateTime createdAt,
			long recordCount,
			long remindCount,
			long bookmarkCount,
			long apiCallCount,
			ZonedDateTime lastActivityAt) {
	}

	public record UserDetail(
			Long userId,
			String nickname,
			String name,
			String email,
			String ageGroup,
			String residenceRegion,
			Integer birthYear,
			boolean remindEnabled,
			ZonedDateTime createdAt,
			long recordCount,
			long remindCount,
			long bookmarkCount,
			long visitedExhibitionCount,
			long apiCallCount,
			ZonedDateTime lastActivityAt,
			List<RecordItem> recentRecords,
			List<RemindItem> recentReminds,
			List<ExhibitionItem> recentBookmarks,
			List<ApiPathCount> apiCallsByPath,
			List<ActivityItem> recentActivity) {
	}

	public record RecordItem(
			Long recordId,
			Long exhibitionId,
			String exhibitionTitle,
			String representativeEmotion,
			String aiStatus,
			String writeMode,
			LocalDate viewedAt,
			ZonedDateTime createdAt) {
	}

	public record RemindItem(
			Long remindId,
			Long recordId,
			String reflection,
			String aiStatus,
			ZonedDateTime createdAt) {
	}

	public record ExhibitionItem(Long exhibitionId, String title, String type) {
	}

	public record ApiPathCount(String path, long count) {
	}

	public record ActivityItem(String method, String path, int status, long durationMs, ZonedDateTime createdAt) {
	}
}
