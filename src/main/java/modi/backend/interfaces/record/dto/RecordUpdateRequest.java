package modi.backend.interfaces.record.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 기록 수정 요청. 본문(≤300자)·감정 키워드(항목당 ≤10자)·미디어를 교체한다.
 * AI 산출 필드는 클라이언트가 보내지 않는다(작성 요청과 동일 계약).
 */
public record RecordUpdateRequest(
		LocalDate viewedAt,
		@NotBlank @Size(max = 300) String content,
		@NotEmpty List<@NotBlank @Size(max = 10) String> emotionCodes,
		List<@Valid RecordMediaRequest> media) {
}
