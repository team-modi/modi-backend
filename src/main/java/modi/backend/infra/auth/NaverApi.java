package modi.backend.infra.auth;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 네이버 REST 엔드포인트 선언형 클라이언트(HTTP Interface, WebClient 백엔드).
 * 토큰 교환은 redirect_uri 대신 state를 쓰고, userinfo는 {@code response} 아래 중첩 구조다.
 */
public interface NaverApi {

	@PostExchange(url = "https://nid.naver.com/oauth2.0/token", contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	Map<String, Object> getToken(@RequestParam MultiValueMap<String, String> form);

	@GetExchange(url = "https://openapi.naver.com/v1/nid/me")
	Map<String, Object> getUserInfo(@RequestHeader("Authorization") String authorization);
}
