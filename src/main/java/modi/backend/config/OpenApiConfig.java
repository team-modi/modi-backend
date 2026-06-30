package modi.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI 문서 메타 + Bearer(access 토큰) 인증 스킴. Swagger UI는 {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	public OpenAPI modiOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("여운 API")
						.version("v1")
						.description("소셜 로그인(카카오·구글) + 자체 JWT 인증 API. 성공은 전부 200, 에러는 code/message로 구분한다."))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME,
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("로그인 응답의 accessToken을 입력한다.")));
	}
}
