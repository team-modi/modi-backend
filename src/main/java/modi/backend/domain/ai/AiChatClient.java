package modi.backend.domain.ai;

/**
 * LLM 채팅 포트(provider 무관). 도메인·애플리케이션은 이 인터페이스만 의존하고,
 * 실제 provider(Claude 등)는 infra 어댑터에서 구현한다(교체 가능 — DIP).
 * 구현체는 Spring/HTTP/provider SDK를 알아도 되지만, 이 포트 자체는 순수 자바만 노출한다.
 */
public interface AiChatClient {

	/**
	 * system 지시 + user 입력으로 한 번의 완성 응답을 받아 텍스트로 반환한다.
	 * provider 미설정 시 {@link AiErrorCode#AI_DISABLED}, 생성 실패 시 {@link AiErrorCode#AI_GENERATION_FAILED}.
	 */
	String complete(String systemPrompt, String userPrompt);

	/**
	 * 구조화 출력 — 응답이 {@code schemaType} 스키마(POJO/record)에 맞게 오도록 강제하고 그 타입으로 반환한다.
	 * 자연어 파싱 없이 계약된 형태의 값을 얻는다(예: 질문 개수 강제). 미지원 provider는 {@link UnsupportedOperationException} 가능.
	 * provider 미설정 시 {@link AiErrorCode#AI_DISABLED}, 생성 실패 시 {@link AiErrorCode#AI_GENERATION_FAILED}.
	 */
	<T> T completeStructured(String systemPrompt, String userPrompt, Class<T> schemaType);
}
