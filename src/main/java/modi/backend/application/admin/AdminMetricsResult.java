package modi.backend.application.admin;

import java.util.List;

/**
 * 관리자 대시보드(집계) 유스케이스 출력. Facade→Controller 사이 DTO(용도명 중첩 record).
 */
public final class AdminMetricsResult {

	private AdminMetricsResult() {
	}

	public record Dashboard(
			long totalUsers,
			long totalRecords,
			long totalReminds,
			long totalExhibitions,
			long catalogExhibitions,
			long customExhibitions,
			long totalApiCalls,
			long remindedRecords,
			double remindConversionRate,
			List<DailyCount> signupsByDay,
			List<DailyCount> recordsByDay,
			List<EmotionCount> topEmotions) {
	}

	public record DailyCount(String date, long count) {
	}

	public record EmotionCount(String emotionCode, long count) {
	}
}
