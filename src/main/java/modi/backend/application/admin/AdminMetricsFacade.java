package modi.backend.application.admin;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.catalog.ExhibitionType;
import modi.backend.infra.activitylog.ActivityLogJpaRepository;
import modi.backend.infra.exhibition.catalog.ExhibitionJpaRepository;
import modi.backend.infra.record.RecordJpaRepository;
import modi.backend.infra.remind.RemindJpaRepository;
import modi.backend.infra.user.UserJpaRepository;

/**
 * 관리자 대시보드 집계. 여러 도메인 Repo를 조합해 읽기 전용 통계를 만든다(상태변경 없음).
 * 일자별 추이는 created_at(UTC) 기준 — 자정 근처 KST 경계는 근사(관리자 지표라 허용).
 */
@Service
@RequiredArgsConstructor
public class AdminMetricsFacade {

	private static final int TOP_EMOTIONS = 10;

	private final UserJpaRepository userRepository;
	private final RecordJpaRepository recordRepository;
	private final RemindJpaRepository remindRepository;
	private final ExhibitionJpaRepository exhibitionRepository;
	private final ActivityLogJpaRepository activityLogRepository;

	@Transactional(readOnly = true)
	public AdminMetricsResult.Dashboard dashboard(int days) {
		ZonedDateTime from = ZonedDateTime.now().minusDays(days);

		long totalRecords = recordRepository.countByDeletedAtIsNull();
		long remindedRecords = remindRepository.countDistinctRemindedRecords();
		double conversionRate = totalRecords == 0 ? 0.0
				: Math.round((double) remindedRecords / totalRecords * 1000.0) / 1000.0;

		return new AdminMetricsResult.Dashboard(
				userRepository.countByDeletedAtIsNull(),
				totalRecords,
				remindRepository.countByDeletedAtIsNull(),
				exhibitionRepository.countByDeletedAtIsNull(),
				exhibitionRepository.countByTypeAndDeletedAtIsNull(ExhibitionType.CATALOG),
				exhibitionRepository.countByTypeAndDeletedAtIsNull(ExhibitionType.CUSTOM),
				activityLogRepository.count(),
				remindedRecords,
				conversionRate,
				toDaily(userRepository.countByDaySince(from)),
				toDaily(recordRepository.countByDaySince(from)),
				toEmotions(recordRepository.topEmotions(PageRequest.of(0, TOP_EMOTIONS))));
	}

	private List<AdminMetricsResult.DailyCount> toDaily(List<Object[]> rows) {
		return rows.stream()
				.map(r -> new AdminMetricsResult.DailyCount(String.valueOf(r[0]), ((Number) r[1]).longValue()))
				.toList();
	}

	private List<AdminMetricsResult.EmotionCount> toEmotions(List<Object[]> rows) {
		return rows.stream()
				.map(r -> new AdminMetricsResult.EmotionCount((String) r[0], ((Number) r[1]).longValue()))
				.toList();
	}
}
