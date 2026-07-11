package modi.backend.application.remind;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import modi.backend.domain.remind.RemindAiStatus;

/**
 * 리마인드 유스케이스 출력. (Controller가 Result → Response로 변환)
 */
public final class RemindResult {

	private RemindResult() {
	}

	/** 소환 대상(회고할 과거 기록) — 화면 1~3 렌더용. sceneImageUrl은 기록 첫 미디어(wf "전시 속, 그 장면"), 없으면 null(FE 포스터 폴백). */
	public record Candidate(Long recordId, int daysAgo, String elapsedLabel, Long exhibitionId,
			String exhibitionTitle, String artist, String posterUrl, String sceneImageUrl, String place, String region,
			LocalDate viewedAt, String originalContent, List<String> originalEmotionCodes) {
	}

	/** 감정 변화 요약 — 원본(그때) vs 회고(지금) + AI 서술. 저장/상세 공통. */
	public record Summary(Long remindId, Long recordId, ZonedDateTime createdAt,
			Long exhibitionId, String exhibitionTitle, String posterUrl, String place, LocalDate viewedAt,
			String beforeContent, List<String> beforeEmotionCodes,
			String reflection, List<String> afterEmotionCodes,
			RemindAiStatus aiStatus, String aiSummary) {
	}

	/** 아카이브 '리마인드' 목록 항목. */
	public record ListItem(Long remindId, Long recordId, ZonedDateTime createdAt, String exhibitionTitle,
			String posterUrl, String place, LocalDate viewedAt, String reflectionPreview,
			List<String> afterEmotionCodes, RemindAiStatus aiStatus, boolean hasAiSummary) {
	}
}
