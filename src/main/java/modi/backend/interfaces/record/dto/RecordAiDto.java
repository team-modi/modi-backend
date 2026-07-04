package modi.backend.interfaces.record.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * AI 감상문 API 요청/응답 DTO 모음(중첩 record). 감상문 저장은 기존 기록 생성 API가 담당한다.
 */
public final class RecordAiDto {

	private RecordAiDto() {
	}

	/** 질문 생성 요청 — 대상 전시. */
	public record QuestionsRequest(@NotNull @Positive Long exhibitionId) {
	}

	/** 질문 생성 응답 — 전시 맥락 기반 질문 목록. */
	public record QuestionsResponse(List<String> questions) {
	}

	/** 질문/답변 한 쌍(각 300자 이내). */
	public record QnaItem(@NotBlank String question, @NotBlank @Size(max = 300) String answer) {
	}

	/** 감상문 다듬기 요청 — 대상 전시 + Q&A 답변. */
	public record ComposeRequest(
			@NotNull @Positive Long exhibitionId,
			@NotEmpty List<@Valid QnaItem> answers) {
	}

	/** 감상문 다듬기 응답 — 다듬어진 본문(≤300자). 사용자가 수정·확정 후 기록 생성 API로 저장한다. */
	public record ComposeResponse(String content) {
	}
}
