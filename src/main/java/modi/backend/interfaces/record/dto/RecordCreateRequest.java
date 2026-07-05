package modi.backend.interfaces.record.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import modi.backend.domain.record.WriteMode;

/**
 * 기록 작성 요청.
 * writeMode = DIRECT(직접 작성) | AI(질문으로 작성 — AI가 다듬은 최종 감상문을 확정해 전송).
 * content 는 두 모드 공통 최종 본문(≤300자). 감정 키워드는 프리셋+커스텀 통합 한글 라벨(항목당 ≤10자).
 * AI 산출 필드(요약·대표감정·카드문구)는 클라이언트가 보내지 않는다(서버측 생성은 별도 — 04_전시기록 와이어프레임).
 */
public record RecordCreateRequest(
		@NotNull @Positive Long exhibitionId,
		@NotNull WriteMode writeMode,
		LocalDate viewedAt,
		@NotBlank @Size(max = 300) String content,
		@NotEmpty List<@NotBlank @Size(max = 10) String> emotionCodes,
		List<@Valid RecordMediaRequest> media) {
}
