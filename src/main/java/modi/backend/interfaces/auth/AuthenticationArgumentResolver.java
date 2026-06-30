package modi.backend.interfaces.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import modi.backend.application.auth.AuthFacade;

/**
 * {@link Authentication} 파라미터를 해석해 {@link LoginUser}를 주입한다.
 * access 토큰은 {@code access_token} HttpOnly 쿠키에서 우선 읽고, 없으면 Authorization Bearer 헤더로 폴백한다
 * (BE 데모 페이지·비쿠키 클라이언트 호환). 무효 시 CoreException → 전역 핸들러가 401.
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
		return LoginUser.from(authFacade.requireAccess(resolveAccessToken(request)));
	}

	/** access_token 쿠키 우선, 없으면 Authorization Bearer 헤더. 둘 다 없으면 {@code BearerToken.resolve}가 NO_ACCESS_TOKEN. */
	private String resolveAccessToken(HttpServletRequest request) {
		if (request != null && request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if (AuthCookie.ACCESS.equals(cookie.getName())
						&& cookie.getValue() != null && !cookie.getValue().isBlank()) {
					return cookie.getValue();
				}
			}
		}
		return BearerToken.resolve(request == null ? null : request.getHeader("Authorization"));
	}
}
