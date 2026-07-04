package modi.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 설정 프로퍼티 등록. (어댑터·Facade는 {@link AiProperties}를 주입받아 사용)
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {
}
