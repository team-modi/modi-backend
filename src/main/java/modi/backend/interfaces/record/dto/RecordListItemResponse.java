package modi.backend.interfaces.record.dto;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import modi.backend.domain.record.WriteMode;

public record RecordListItemResponse(
		Long recordId,
		Long exhibitionId,
		String thumbnailUrl,
		String aiSummary,
		String representativeEmotion,
		// 아카이브 카드의 감정 태그(제목 아래). 감정 코드는 프리셋+커스텀 통합 한글 라벨(예: 강렬한·서정적인).
		List<String> emotionCodes,
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
