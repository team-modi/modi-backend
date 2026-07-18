package modi.backend.infra.exhibition.sync.gemini;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Gemini generateContent 요청/응답 바인딩(외곽 클래스 1개에 중첩 record로 묶음 — 컨벤션).
 * 응답에는 우리가 쓰지 않는 필드(role·finishReason·usageMetadata·thoughtSignature 등)가 섞여 오므로
 * 응답 계열 record는 {@code ignoreUnknown}으로 관대하게 파싱한다(모델 버전 따라 필드가 늘어도 안전).
 */
public final class GeminiDto {

	private GeminiDto() {
	}

	// ----- 요청 -----

	/** 요청 루트. system 지시 + user 콘텐츠 + 생성 설정(구조화 출력 강제). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Request(SystemInstruction systemInstruction, List<Content> contents,
			GenerationConfig generationConfig) {
	}

	public record SystemInstruction(List<Part> parts) {
	}

	/** 요청·응답 공용. 응답의 {@code role} 등 여분 필드는 무시한다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Content(List<Part> parts) {
	}

	/** 요청·응답 공용. 응답의 {@code thoughtSignature} 등 여분 필드는 무시한다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Part(String text) {
	}

	/**
	 * 구조화 출력 설정. {@code responseMimeType=text/x.enum} + enum 스키마로 응답을 마스터 장르 중 하나로 강제한다
	 * (자연어 파싱 없이 계약된 값 확보 — Gemini structured output).
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GenerationConfig(String responseMimeType, ResponseSchema responseSchema) {
	}

	/**
	 * enum/array 제약 스키마. 직렬화 키는 Gemini 규격({@code type}/{@code enum}/{@code items})에 맞춘다.
	 * NON_NULL이라 단건(STRING+enum)이면 items가, 배치(ARRAY+items)면 enum이 각각 생략된다.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ResponseSchema(String type, @JsonProperty("enum") List<String> enumValues, ResponseSchema items) {

		/** 단건 — 마스터 장르 중 하나(STRING enum). */
		public static ResponseSchema ofEnum(List<String> values) {
			return new ResponseSchema("STRING", values, null);
		}

		/** 배치 — 마스터 장르 enum의 배열(ARRAY of STRING enum). 여러 전시를 한 응답(JSON 배열)으로 받는다. */
		public static ResponseSchema ofEnumArray(List<String> values) {
			return new ResponseSchema("ARRAY", null, ofEnum(values));
		}
	}

	// ----- 응답 -----

	/**
	 * 응답 루트. 후보 첫 파트의 텍스트가 곧 분류 결과다.
	 * <p>
	 * {@code modelVersion}은 <b>실제 서빙 모델</b>이다 — 요청 모델({@code app.exhibition.genre.gemini.model})은 별칭(alias)일
	 * 수 있어 진실은 응답에 있다. 정준층({@code exhibition_genre.model})이 이 값을 기록해 모델 업그레이드 시 구모델 산출분만
	 * 선별 재분류한다. {@code ignoreUnknown}은 <b>선언된 컴포넌트의 바인딩을 막지 않는다</b>(선언 안 된 여분 필드만 무시).
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Response(List<Candidate> candidates, String modelVersion) {

		/** 첫 후보의 첫 파트 텍스트(없으면 null). */
		public String firstText() {
			if (candidates == null || candidates.isEmpty()) {
				return null;
			}
			Content content = candidates.get(0).content();
			if (content == null || content.parts() == null || content.parts().isEmpty()) {
				return null;
			}
			String text = content.parts().get(0).text();
			return text == null ? null : text.trim();
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Candidate(Content content) {
	}
}
