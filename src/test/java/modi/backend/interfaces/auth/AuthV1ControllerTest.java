package modi.backend.interfaces.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import modi.backend.application.auth.AuthFacade;
import modi.backend.config.CookieProperties;
import modi.backend.config.OAuthProperties;

/**
 * 전역 예외 처리(@RestControllerAdvice)가 컨트롤러의 에러를 ApiResponse로 일관되게 매핑하는지 검증.
 */
@WebMvcTest(AuthV1Controller.class)
class AuthV1ControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AuthFacade authFacade;

	@MockitoBean
	OAuthProperties oauthProperties;

	@MockitoBean
	CookieProperties cookieProperties;

	@Test
	@DisplayName("허용되지 않은 redirectUri → 400 INVALID_REDIRECT_URI")
	void login_허용외_redirectUri() throws Exception {
		given(oauthProperties.isAllowedRedirectUri(anyString())).willReturn(false);

		mockMvc.perform(post("/api/v1/auth/login/kakao")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"abc\",\"redirectUri\":\"https://evil.com\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("INVALID_REDIRECT_URI"));
	}

	@Test
	@DisplayName("@Valid 실패(빈 code) → 400 INVALID_INPUT + fieldErrors")
	void login_검증실패() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login/kakao")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"\",\"redirectUri\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.fieldErrors").isArray());
	}

	@Test
	@DisplayName("refresh 쿠키 없음 → 401 NO_REFRESH_TOKEN")
	void refresh_쿠키없음() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("NO_REFRESH_TOKEN"));
	}

	@Test
	@DisplayName("Bearer 없음 → 401 NO_ACCESS_TOKEN")
	void me_Bearer없음() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("NO_ACCESS_TOKEN"));
	}
}
