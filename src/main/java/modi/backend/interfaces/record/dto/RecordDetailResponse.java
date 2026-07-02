package modi.backend.interfaces.record.dto;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import modi.backend.domain.record.AiStatus;
import modi.backend.domain.record.WriteMode;

public record RecordDetailResponse(
		Long recordId,
		Long exhibitionId,
		WriteMode writeMode,
		AiStatus aiStatus,
		LocalDate viewedAt,
		String content,
		String aiSummary,
		List<String> aiKeywords,
		List<String> userKeywords,
		String representativeEmotion,
		String cardPhrase,
		List<String> emotionCodes,
		boolean bookmarked,
		List<RecordMediaResponse> media,
		ZonedDateTime createdAt,
		ZonedDateTime updatedAt,
		String exhibitionTitle,
		String exhibitionType,
		String exhibitionPosterUrl,
		String exhibitionPlace,
		String exhibitionRegion,
		String exhibitionCategory,
		LocalDate exhibitionStartDate,
		LocalDate exhibitionEndDate) {
}
