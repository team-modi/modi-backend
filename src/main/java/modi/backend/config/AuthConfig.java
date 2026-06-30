package modi.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * auth 모듈 설정 프로퍼티 등록.
 */
@Configuration
@EnableConfigurationProperties({OAuthProperties.class, JwtProperties.class, CookieProperties.class})
public class AuthConfig {
}
