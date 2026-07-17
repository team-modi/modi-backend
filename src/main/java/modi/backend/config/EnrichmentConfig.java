package modi.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 통합 보강 작업큐 관련 설정 바인딩 등록.
 */
@Configuration
@EnableConfigurationProperties(EnrichmentProperties.class)
public class EnrichmentConfig {
}
