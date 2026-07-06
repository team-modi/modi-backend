package modi.backend.application.record;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 질문 생성용 구조화 출력 스키마. 3개 필드를 필수로 두어 "질문 정확히 3개"를 API 레벨에서 강제한다
 * (자연어 JSON 파싱·폴백 불필요). 각 필드는 전시 감상을 이끌어내는 짧은 한국어 질문.
 */
@JsonClassDescription("전시 감상을 이끌어내는 짧은 한국어 질문 3개")
public record AiQuestionsOutput(
		@JsonPropertyDescription("첫 번째 질문") String question1,
		@JsonPropertyDescription("두 번째 질문") String question2,
		@JsonPropertyDescription("세 번째 질문") String question3) {
}
