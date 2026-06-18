package modi.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 허용 오리진 설정. application.yaml의 {@code app.cors.*} 값을 바인딩한다.
 * 오리진은 비밀이 아니므로 일반 설정값(yaml/환경변수)으로 둔다.
 *
 * @param allowedOriginPatterns 허용할 오리진 패턴 목록 (예: https://*.vercel.app)
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOriginPatterns) {

	public CorsProperties {
		if (allowedOriginPatterns == null || allowedOriginPatterns.isEmpty()) {
			// 안전망: 설정이 비어 있으면 로컬 개발용만 허용
			allowedOriginPatterns = List.of("http://localhost:3000");
		}
	}
}
