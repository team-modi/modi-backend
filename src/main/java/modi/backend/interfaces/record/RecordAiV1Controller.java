package modi.backend.interfaces.record;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import modi.backend.application.record.RecordAiCriteria;
import modi.backend.application.record.RecordAiFacade;
import modi.backend.application.record.RecordAiResult;
import modi.backend.interfaces.auth.Authentication;
import modi.backend.interfaces.auth.LoginUser;
import modi.backend.interfaces.record.dto.RecordAiDto;
import modi.backend.support.response.ApiResponse;

/**
 * AI 감상문 API('질문으로 작성' 플로우). 로그인 전용 — access 토큰의 사용자로 동작한다.
 * 감상문 저장은 이 컨트롤러가 하지 않는다(사용자 확정 후 기존 기록 생성 API로 저장).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/records/ai")
public class RecordAiV1Controller implements RecordAiV1ApiSpec {

	private final RecordAiFacade recordAiFacade;

	@Override
	@PostMapping("/questions")
	public ApiResponse<RecordAiDto.QuestionsResponse> questions(
			@Parameter(hidden = true) @Authentication LoginUser loginUser,
			@Valid @RequestBody RecordAiDto.QuestionsRequest request) {
		RecordAiResult.Questions result = recordAiFacade.questions(
				new RecordAiCriteria.Questions(loginUser.userId(), request.exhibitionId()));
		return ApiResponse.success(new RecordAiDto.QuestionsResponse(result.questions()));
	}

	@Override
	@PostMapping("/compose")
	public ApiResponse<RecordAiDto.ComposeResponse> compose(
			@Parameter(hidden = true) @Authentication LoginUser loginUser,
			@Valid @RequestBody RecordAiDto.ComposeRequest request) {
		RecordAiResult.Compose result = recordAiFacade.compose(new RecordAiCriteria.Compose(
				loginUser.userId(), request.exhibitionId(),
				request.answers().stream()
						.map(a -> new RecordAiCriteria.QnaPair(a.question(), a.answer()))
						.toList()));
		return ApiResponse.success(new RecordAiDto.ComposeResponse(result.content()));
	}
}
