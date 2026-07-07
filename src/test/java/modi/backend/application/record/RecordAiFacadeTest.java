package modi.backend.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import modi.backend.application.exhibition.ExhibitionFacade;
import modi.backend.application.exhibition.ExhibitionResult;
import modi.backend.domain.ai.AiChatClient;
import modi.backend.support.error.CoreException;

@ExtendWith(MockitoExtension.class)
class RecordAiFacadeTest {

	@Mock
	AiChatClient aiChatClient;

	@Mock
	ExhibitionFacade exhibitionFacade;

	@Mock
	AiRateLimiter aiRateLimiter;

	@InjectMocks
	RecordAiFacade facade;

	@Test
	@DisplayName("questions — 구조화 출력(3개 필드)을 질문 목록으로 변환한다")
	void questions_구조화출력() {
		given(exhibitionFacade.getForSnapshot(any(), any())).willReturn(detail());
		given(aiChatClient.completeStructured(anyString(), anyString(), eq(AiQuestionsOutput.class)))
				.willReturn(new AiQuestionsOutput("가장 오래 남은 장면은?", "어떤 감정이었나요?", "한 문장으로 남긴다면?"));

		RecordAiResult.Questions result = facade.questions(new RecordAiCriteria.Questions(1L, 10L));

		assertThat(result.questions()).containsExactly(
				"가장 오래 남은 장면은?", "어떤 감정이었나요?", "한 문장으로 남긴다면?");
	}

	@Test
	@DisplayName("questions — 빈 필드는 걸러내고, 모두 비면 실패한다")
	void questions_모두빈값_실패() {
		given(exhibitionFacade.getForSnapshot(any(), any())).willReturn(detail());
		given(aiChatClient.completeStructured(anyString(), anyString(), eq(AiQuestionsOutput.class)))
				.willReturn(new AiQuestionsOutput(" ", "", null));

		assertThatThrownBy(() -> facade.questions(new RecordAiCriteria.Questions(1L, 10L)))
				.isInstanceOf(CoreException.class);
	}

	@Test
	@DisplayName("compose — Q&A로 감상문 본문을 생성하고 300자로 자른다")
	void compose_본문생성_및_클램프() {
		given(exhibitionFacade.getForSnapshot(any(), any())).willReturn(detail());
		String longEssay = "가".repeat(400);
		given(aiChatClient.complete(anyString(), anyString())).willReturn(longEssay);

		RecordAiResult.Compose result = facade.compose(new RecordAiCriteria.Compose(
				1L, 10L, List.of(new RecordAiCriteria.QnaPair("q", "a"))));

		assertThat(result.content()).hasSize(300);
	}

	@Test
	@DisplayName("compose — 답변이 비면 AI_GENERATION_FAILED")
	void compose_빈답변_실패() {
		assertThatThrownBy(() -> facade.compose(new RecordAiCriteria.Compose(1L, 10L, List.of())))
				.isInstanceOf(CoreException.class);
	}

	private ExhibitionResult.Detail detail() {
		return new ExhibitionResult.Detail(10L, "CATALOG", "모네: 빛을 그리다", "http://p",
				LocalDate.now(), null, "예술의전당", "SEOUL", "PAINTING", null,
				"인상주의 대표전", null, null, List.of("클로드 모네"), List.of("인상주의"),
				null, null, null, null, null, null, null, 0L, null, null);
	}
}
