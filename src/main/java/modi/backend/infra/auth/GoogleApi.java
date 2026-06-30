package modi.backend.infra.auth;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 구글 REST 엔드포인트 선언형 클라이언트(HTTP Interface, WebClient 백엔드).
 */
public interface GoogleApi {

	@PostExchange(url = "https://oauth2.googleapis.com/token", contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	Map<String, Object> getToken(@RequestParam MultiValueMap<String, String> form);

	@GetExchange(url = "https://www.googleapis.com/oauth2/v2/userinfo")
	Map<String, Object> getUserInfo(@RequestHeader("Authorization") String authorization);
}
