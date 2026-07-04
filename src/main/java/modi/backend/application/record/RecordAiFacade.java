package modi.backend.application.record;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import modi.backend.application.exhibition.ExhibitionFacade;
import modi.backend.application.exhibition.ExhibitionResult;
import modi.backend.domain.ai.AiChatClient;
import modi.backend.domain.ai.AiErrorCode;
import modi.backend.support.error.CoreException;

/**
 * AI 감상문 유스케이스 조율(멀티스텝).
 * (1) 전시 상세를 도구처럼 조회해 맥락을 만들고 → (2) LLM 포트로 질문 생성/감상문 다듬기를 수행한다.
 * 상태 변경은 없고(저장은 기존 기록 생성 API가 담당), LLM provider는 {@link AiChatClient} 포트로만 의존한다.
 */
@Service
@RequiredArgsConstructor
public class RecordAiFacade {

	/** 감상문 본문 최대 길이(와이어프레임 0/300). */
	private static final int CONTENT_MAX_LENGTH = 300;

	private final AiChatClient aiChatClient;
	private final ExhibitionFacade exhibitionFacade;
	private final ObjectMapper objectMapper = new ObjectMapper();

	/** 전시 맥락 기반 질문 3개 생성. "다른 질문 보기"는 이 호출을 다시 하면 된다. */
	public RecordAiResult.Questions questions(RecordAiCriteria.Questions criteria) {
		ExhibitionResult.Detail exhibition = exhibitionFacade.getForSnapshot(criteria.exhibitionId(), criteria.userId());
		String system = """
				너는 전시 관람 감상을 이끌어내는 다정한 인터뷰어야.
				사용자가 답하기 쉬운, 이 전시에 어울리는 짧은 한국어 질문 3개를 만들어.
				추상적이지 않고 구체적인 장면·감정·의미를 떠올리게 하는 질문이어야 해.
				반드시 문자열 3개짜리 JSON 배열로만 답하고, 다른 말은 붙이지 마.""";
		String user = exhibitionContext(exhibition) + "\n\n위 전시에 대한 감상 질문 3개를 JSON 배열로 만들어 줘.";
		String raw = aiChatClient.complete(system, user);
		return new RecordAiResult.Questions(parseQuestions(raw));
	}

	/** Q&A 답변을 바탕으로 감상문 본문 생성(동기). "다시 다듬기"는 이 호출을 다시 하면 된다. */
	public RecordAiResult.Compose compose(RecordAiCriteria.Compose criteria) {
		if (criteria.answers() == null || criteria.answers().isEmpty()) {
			throw new CoreException(AiErrorCode.AI_GENERATION_FAILED, "답변이 비어 있습니다.");
		}
		ExhibitionResult.Detail exhibition = exhibitionFacade.getForSnapshot(criteria.exhibitionId(), criteria.userId());
		String system = """
				너는 사용자의 답변을 따뜻하고 진솔한 1인칭 감상문으로 다듬는 작가야.
				한국어 한 단락, 300자 이내로 쓰고, 답변에 없는 사실을 지어내지 마.
				이모지·머리말·따옴표 없이 감상문 본문만 출력해.""";
		StringBuilder user = new StringBuilder(exhibitionContext(exhibition)).append("\n\n[질문과 답변]\n");
		int i = 1;
		for (RecordAiCriteria.QnaPair qna : criteria.answers()) {
			user.append("Q").append(i).append(". ").append(qna.question()).append('\n')
					.append("A").append(i).append(". ").append(qna.answer()).append("\n\n");
			i++;
		}
		user.append("위 답변을 바탕으로 감상문을 작성해 줘.");
		String raw = aiChatClient.complete(system, user.toString());
		return new RecordAiResult.Compose(clamp(raw));
	}

	/** 전시 스냅샷을 프롬프트용 맥락 문자열로 변환(있는 필드만). */
	private String exhibitionContext(ExhibitionResult.Detail exhibition) {
		StringBuilder sb = new StringBuilder("[전시 정보]\n");
		sb.append("제목: ").append(exhibition.title()).append('\n');
		if (exhibition.place() != null) {
			sb.append("장소: ").append(exhibition.place()).append('\n');
		}
		if (exhibition.category() != null) {
			sb.append("카테고리: ").append(exhibition.category()).append('\n');
		}
		if (exhibition.artists() != null && !exhibition.artists().isEmpty()) {
			sb.append("작가: ").append(String.join(", ", exhibition.artists())).append('\n');
		}
		if (exhibition.description() != null && !exhibition.description().isBlank()) {
			sb.append("설명: ").append(exhibition.description()).append('\n');
		}
		return sb.toString().trim();
	}

	/** LLM 응답을 문자열 리스트로 파싱. JSON 배열 우선, 실패 시 줄 단위 폴백. 비면 생성 실패로 본다. */
	private List<String> parseQuestions(String raw) {
		List<String> questions = tryParseJsonArray(raw);
		if (questions.isEmpty()) {
			questions = fallbackLines(raw);
		}
		if (questions.isEmpty()) {
			throw new CoreException(AiErrorCode.AI_GENERATION_FAILED, "질문을 생성하지 못했습니다.");
		}
		return questions;
	}

	private List<String> tryParseJsonArray(String raw) {
		int start = raw.indexOf('[');
		int end = raw.lastIndexOf(']');
		if (start < 0 || end <= start) {
			return List.of();
		}
		try {
			String[] parsed = objectMapper.readValue(raw.substring(start, end + 1), String[].class);
			List<String> result = new ArrayList<>();
			for (String q : parsed) {
				if (q != null && !q.isBlank()) {
					result.add(q.trim());
				}
			}
			return result;
		} catch (Exception e) {
			return List.of();
		}
	}

	private List<String> fallbackLines(String raw) {
		List<String> result = new ArrayList<>();
		for (String line : raw.split("\\r?\\n")) {
			String cleaned = line.replaceFirst("^\\s*[-*\\d.\\)\\s]+", "").trim();
			if (!cleaned.isBlank()) {
				result.add(cleaned);
			}
		}
		return result;
	}

	private String clamp(String content) {
		String trimmed = content == null ? "" : content.trim();
		if (trimmed.isBlank()) {
			throw new CoreException(AiErrorCode.AI_GENERATION_FAILED, "감상문을 생성하지 못했습니다.");
		}
		return trimmed.length() > CONTENT_MAX_LENGTH ? trimmed.substring(0, CONTENT_MAX_LENGTH) : trimmed;
	}
}
