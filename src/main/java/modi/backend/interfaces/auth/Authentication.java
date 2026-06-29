package modi.backend.interfaces.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 사용자 주입 마커. 컨트롤러 파라미터에 붙이면 access 토큰을 검증해 {@link LoginUser}를 주입한다.
 * (해석은 {@link AuthenticationArgumentResolver})
 * <pre>public X handler(@Authentication LoginUser user) { ... }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Authentication {
}
