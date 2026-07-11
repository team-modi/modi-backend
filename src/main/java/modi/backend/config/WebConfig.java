package modi.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.List;

import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;
import modi.backend.interfaces.activitylog.ActivityLogInterceptor;
import modi.backend.interfaces.admin.AdminAuthInterceptor;
import modi.backend.interfaces.auth.AuthenticationArgumentResolver;
import modi.backend.interfaces.auth.OptionalAuthenticationArgumentResolver;

/**
 * 전역 웹 설정. CORS + {@code @Authentication} 인자 리졸버 등록.
 * 허용 오리진은 {@link CorsProperties}(application.yaml)에서 주입받아,
 * 도메인이 바뀌어도 코드 수정 없이 설정/환경변수로 관리할 수 있게 한다.
 */
@Configuration
@EnableConfigurationProperties({CorsProperties.class, AdminProperties.class})
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final CorsProperties corsProperties;
	private final AuthenticationArgumentResolver authenticationArgumentResolver;
	private final OptionalAuthenticationArgumentResolver optionalAuthenticationArgumentResolver;
	private final AdminAuthInterceptor adminAuthInterceptor;
	private final ActivityLogInterceptor activityLogInterceptor;

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(authenticationArgumentResolver);
		resolvers.add(optionalAuthenticationArgumentResolver);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// 관리자 게이트: /api-admin/** 은 admin_session 세션 필요. 단 로그인 엔드포인트는 제외(그래야 로그인 가능).
		registry.addInterceptor(adminAuthInterceptor)
				.addPathPatterns("/api-admin/**")
				.excludePathPatterns("/api-admin/v1/login");
		// 활동 로그: 고객 API(/api/v1/**)의 인증 요청만 비동기 기록(actuator·정적·admin 제외).
		registry.addInterceptor(activityLogInterceptor).addPathPatterns("/api/v1/**");
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
