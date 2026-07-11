package modi.backend.interfaces.remind.dto;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import modi.backend.application.remind.RemindResult;
import modi.backend.domain.remind.RemindAiStatus;

/**
 * 리마인드 API 요청/응답 DTO 모음(중첩 record).
 */
public final class RemindDto {

	private RemindDto() {
	}

	/** 리마인드 저장 요청 — 대상 기록 + 지금 다시 남기는 감정(선택) + 소감(필수, ≤300). */
	public record SaveRequest(
			@Schema(description = "회고할 기록 ID", example = "128")
			@NotNull @Positive Long recordId,
			@Schema(description = "지금 다시 남기는 감정 코드(선택, 각 10자 이내)", example = "[\"슬픔\", \"서정적인\"]")
			List<@Size(max = 10) String> emotionCodes,
			@Schema(description = "한 줄로 남기고 싶은 문장(오늘의 여운). 필수", example = "다시 보니 슬픈 분위기가 더 다가온다")
			@NotBlank @Size(max = 300) String reflection) {
	}

	/** 소환 대상(회고할 과거 기록) 응답. */
	public record CandidateResponse(Long recordId, int daysAgo, String elapsedLabel, Long exhibitionId,
			String exhibitionTitle, String artist, String posterUrl, String sceneImageUrl, String place, String region,
			LocalDate viewedAt, String originalContent, List<String> originalEmotionCodes) {

		public static CandidateResponse from(RemindResult.Candidate c) {
			return new CandidateResponse(c.recordId(), c.daysAgo(), c.elapsedLabel(), c.exhibitionId(),
					c.exhibitionTitle(), c.artist(), c.posterUrl(), c.sceneImageUrl(), c.place(), c.region(),
					c.viewedAt(), c.originalContent(), c.originalEmotionCodes());
		}
	}

	/** 감정 변화 요약 — 원본(그때) vs 회고(지금) + AI 서술. 저장/상세 공통. */
	public record SummaryResponse(Long remindId, Long recordId, ZonedDateTime createdAt,
			ExhibitionRef exhibition, Side before, Side after, RemindAiStatus aiStatus, String aiSummary) {

		public record ExhibitionRef(Long exhibitionId, String title, String posterUrl, String place,
				LocalDate viewedAt) {
		}

		/** before=그날의 감상(text=content) / after=오늘의 여운(text=reflection). */
		public record Side(String text, List<String> emotionCodes) {
		}

		public static SummaryResponse from(RemindResult.Summary s) {
			return new SummaryResponse(s.remindId(), s.recordId(), s.createdAt(),
					new ExhibitionRef(s.exhibitionId(), s.exhibitionTitle(), s.posterUrl(), s.place(), s.viewedAt()),
					new Side(s.beforeContent(), s.beforeEmotionCodes()),
					new Side(s.reflection(), s.afterEmotionCodes()),
					s.aiStatus(), s.aiSummary());
		}
	}

	/** 아카이브 '리마인드' 목록 항목. beforeEmotionCodes=원본 기록의 감정(감정 변화-유지/반전 필터용). */
	public record ListItemResponse(Long remindId, Long recordId, ZonedDateTime createdAt, String exhibitionTitle,
			String posterUrl, String place, LocalDate viewedAt, String reflectionPreview,
			@Schema(description = "원본 기록의 감정 코드(그때). 원본 삭제 시 빈 리스트") List<String> beforeEmotionCodes,
			@Schema(description = "회고의 감정 코드(지금)") List<String> emotionCodes,
			RemindAiStatus aiStatus, boolean hasAiSummary) {

		public static ListItemResponse from(RemindResult.ListItem i) {
			return new ListItemResponse(i.remindId(), i.recordId(), i.createdAt(), i.exhibitionTitle(),
					i.posterUrl(), i.place(), i.viewedAt(), i.reflectionPreview(), i.beforeEmotionCodes(),
					i.afterEmotionCodes(), i.aiStatus(), i.hasAiSummary());
		}
	}
}
