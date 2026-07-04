package modi.backend.interfaces.record;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import modi.backend.interfaces.auth.LoginUser;
import modi.backend.interfaces.record.dto.RecordAiDto;
import modi.backend.support.response.ApiResponse;

/**
 * AI 감상문 API Swagger 스펙. (MVC 어노테이션은 {@link RecordAiV1Controller})
 * 로그인 전용. api-key 미설정 환경에서는 503 AI_DISABLED.
 */
@Tag(name = "Record AI", description = "질문으로 작성 — 전시 맥락 질문 생성 · Q&A 감상문 다듬기")
@SecurityRequirement(name = "bearerAuth")
public interface RecordAiV1ApiSpec {

	@Operation(summary = "감상 질문 생성", description = """
			전시 맥락(제목·장소·설명 등)을 반영해 답하기 쉬운 질문 3개를 생성한다.
			- '다른 질문 보기'는 이 API를 다시 호출하면 된다.
			- 로그인 전용. AI 미설정 시 503 AI_DISABLED.""")
	ApiResponse<RecordAiDto.QuestionsResponse> questions(
			@Parameter(hidden = true) LoginUser loginUser,
			RecordAiDto.QuestionsRequest request);

	@Operation(summary = "감상문 다듬기", description = """
			Q&A 답변을 바탕으로 감상문 본문(≤300자)을 생성한다(동기).
			- '다시 다듬기'는 이 API를 다시 호출하면 된다.
			- 반환된 본문을 사용자가 수정·확정한 뒤 기록 생성 API(POST /api/v1/records, writeMode=AI)로 저장한다.
			- 로그인 전용. AI 미설정 시 503 AI_DISABLED.""")
	ApiResponse<RecordAiDto.ComposeResponse> compose(
			@Parameter(hidden = true) LoginUser loginUser,
			RecordAiDto.ComposeRequest request);
}
