package modi.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 자체 JWT 설정 바인딩. {@code app.jwt.*}. secret은 환경변수 주입 권장.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long accessTtlSeconds, long refreshTtlSeconds) {
}
