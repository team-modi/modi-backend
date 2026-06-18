package modi.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 전역 CORS 설정. 허용 오리진은 {@link CorsProperties}(application.yaml)에서 주입받아,
 * 도메인이 바뀌어도 코드 수정 없이 설정/환경변수로 관리할 수 있게 한다.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

	private final CorsProperties corsProperties;

	public WebConfig(CorsProperties corsProperties) {
		this.corsProperties = corsProperties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				// allowCredentials(true)와 함께 쓰려면 "*"가 아닌 allowedOriginPatterns 사용
				.allowedOriginPatterns(corsProperties.allowedOriginPatterns().toArray(String[]::new))
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.exposedHeaders("Authorization", "Location")
				.allowCredentials(true)
				.maxAge(3600);
	}
}
