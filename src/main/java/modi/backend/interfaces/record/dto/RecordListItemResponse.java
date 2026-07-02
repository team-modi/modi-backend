package modi.backend.interfaces.record.dto;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import modi.backend.domain.record.WriteMode;

public record RecordListItemResponse(
		Long recordId,
		Long exhibitionId,
		String thumbnailUrl,
		String aiSummary,
		String representativeEmotion,
		boolean bookmarked,
		WriteMode writeMode,
		LocalDate viewedAt,
		ZonedDateTime createdAt,
		String exhibitionTitle,
		String exhibitionType,
		String exhibitionPosterUrl,
		String exhibitionPlace,
		String exhibitionRegion,
		String exhibitionCategory,
		LocalDate exhibitionStartDate,
		LocalDate exhibitionEndDate) {
}
