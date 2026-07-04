package modi.backend.infra.ai.claude;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;

import modi.backend.config.AiProperties;
import modi.backend.domain.ai.AiChatClient;
import modi.backend.domain.ai.AiErrorCode;
import modi.backend.support.error.CoreException;

/**
 * {@link AiChatClient}의 Claude(Anthropic) 어댑터. 공식 anthropic-java SDK로 Messages API 호출.
 * api-key 미설정이면 클라이언트를 만들지 않고, 호출 시 {@link AiErrorCode#AI_DISABLED}를 던진다
 * (AI만 비활성, 나머지 기능은 정상). 모델·max-tokens는 {@link AiProperties} 설정값을 사용한다.
 */
@Component
public class ClaudeAiChatClient implements AiChatClient {

	private final AiProperties properties;
	private final AnthropicClient client; // api-key 미설정 시 null

	public ClaudeAiChatClient(AiProperties properties) {
		this.properties = properties;
		this.client = properties.isConfigured()
				? AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build()
				: null;
	}

	@Override
	public String complete(String systemPrompt, String userPrompt) {
		if (client == null) {
			throw new CoreException(AiErrorCode.AI_DISABLED);
		}
		try {
			MessageCreateParams params = MessageCreateParams.builder()
					.model(properties.model())
					.maxTokens(properties.maxTokens())
					.system(systemPrompt)
					.addUserMessage(userPrompt)
					.build();
			Message response = client.messages().create(params);
			return response.content().stream()
					.flatMap(block -> block.text().stream())
					.map(text -> text.text())
					.collect(Collectors.joining("\n"))
					.trim();
		} catch (CoreException e) {
			throw e;
		} catch (Exception e) {
			throw new CoreException(AiErrorCode.AI_GENERATION_FAILED, e.getMessage());
		}
	}
}
