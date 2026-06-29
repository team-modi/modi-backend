package modi.backend.interfaces.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import modi.backend.application.auth.AuthFacade;

/**
 * {@link Authentication} 파라미터를 해석해 {@link LoginUser}를 주입한다.
 * Authorization 헤더의 Bearer access 토큰을 검증(무효 시 CoreException → 전역 핸들러가 401).
 */
@Component
@RequiredArgsConstructor
public class AuthenticationArgumentResolver implements HandlerMethodArgumentResolver {

	private final AuthFacade authFacade;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(Authentication.class)
				&& LoginUser.class.equals(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
								  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		String token = BearerToken.resolve(request == null ? null : request.getHeader("Authorization"));
		return LoginUser.from(authFacade.requireAccess(token));
	}
}
