package modi.backend.infra.ai.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import modi.backend.application.record.AiQuestionsOutput;

/**
 * Gemini responseSchema 변환 순수 단위 테스트(컨텍스트 없음).
 * 구조화 출력이 "정확한 형태"를 강제하도록 type/properties/required/propertyOrdering이 올바른지 검증한다.
 */
class GeminiSchemaTest {

	@Test
	@DisplayName("record의 3개 String 필드를 OBJECT 스키마로 변환하고 순서·필수·설명을 보존한다")
	@SuppressWarnings("unchecked")
	void 질문_record를_스키마로_변환() {
		Map<String, Object> schema = GeminiSchema.of(AiQuestionsOutput.class);

		assertThat(schema.get("type")).isEqualTo("OBJECT");
		assertThat((List<String>) schema.get("required"))
				.containsExactly("question1", "question2", "question3");
		assertThat((List<String>) schema.get("propertyOrdering"))
				.containsExactly("question1", "question2", "question3");

		Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
		Map<String, Object> question1 = (Map<String, Object>) properties.get("question1");
		assertThat(question1.get("type")).isEqualTo("STRING");
		assertThat(question1.get("description")).isEqualTo("첫 번째 질문");
	}

	@Test
	@DisplayName("정수·enum·List 필드를 각각 INTEGER·enum STRING·ARRAY로 매핑한다")
	@SuppressWarnings("unchecked")
	void 다양한_타입을_매핑() {
		Map<String, Object> schema = GeminiSchema.of(Sample.class);
		Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

		assertThat(((Map<String, Object>) properties.get("count")).get("type")).isEqualTo("INTEGER");

		Map<String, Object> mood = (Map<String, Object>) properties.get("mood");
		assertThat(mood.get("type")).isEqualTo("STRING");
		assertThat((List<String>) mood.get("enum")).containsExactly("CALM", "INTENSE");

		Map<String, Object> tags = (Map<String, Object>) properties.get("tags");
		assertThat(tags.get("type")).isEqualTo("ARRAY");
		assertThat(((Map<String, Object>) tags.get("items")).get("type")).isEqualTo("STRING");
	}

	enum Mood {
		CALM, INTENSE
	}

	record Sample(@JsonPropertyDescription("개수") int count, Mood mood, List<String> tags) {
	}
}
