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
}
