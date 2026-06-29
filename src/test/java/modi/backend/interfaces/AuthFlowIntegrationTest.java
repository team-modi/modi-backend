package modi.backend.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;
import modi.backend.TestcontainersConfiguration;
import modi.backend.infra.auth.KakaoApi;

/**
 * 프론트 → 백엔드 요청/응답 정상 동작 end-to-end 검증.
 * 외부 카카오 HTTP({@link KakaoApi})만 목으로 두고, 컨트롤러·@Authentication 리졸버·JWT 발급/검증·
 * 쿠키·전역 예외·DB(Testcontainers)는 실제로 태운다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

	private static final String REDIRECT_URI = "http://localhost:3000/login"; // application.yaml 화이트리스트

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	KakaoApi kakaoApi;

	@Test
	@DisplayName("로그인 → /me → /refresh → 온보딩 → 연동까지 정상 응답")
	void 전체_플로우_정상응답() throws Exception {
		// 카카오 토큰 교환 + userinfo 응답을 고정(중첩 구조)
		given(kakaoApi.getToken(any())).willReturn(Map.of("access_token", "kakao-access-token"));
		given(kakaoApi.getUserInfo(anyString())).willReturn(Map.of(
				"id", 1234567,
				"kakao_account", Map.of(
						"email", "user@kakao.com",
						"profile", Map.of("nickname", "카카오유저"))));

		// 1) 로그인: code → 자체 JWT 발급. access는 본문, refresh는 HttpOnly 쿠키.
		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"auth-code\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meta.result").value("SUCCESS"))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.user.provider").value("kakao"))
				.andExpect(jsonPath("$.data.user.email").value("user@kakao.com"))
				.andExpect(jsonPath("$.data.user.profileCompleted").value(false))
				.andReturn();

		String body = loginResult.getResponse().getContentAsString();
		String accessToken = JsonPath.read(body, "$.data.accessToken");
		Integer userId = JsonPath.read(body, "$.data.user.userId");

		// refresh는 HttpOnly 쿠키로 내려간다(Set-Cookie 헤더 직접 검증)
		String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
		assertThat(setCookie).isNotNull().contains("HttpOnly").startsWith("refresh_token=");
		String refreshToken = extractRefreshToken(setCookie);
		assertThat(accessToken).isNotBlank();
		assertThat(refreshToken).isNotBlank();

		// 2) /me: Bearer access → @Authentication 주입으로 내 정보
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(userId))
				.andExpect(jsonPath("$.data.provider").value("kakao"))
				.andExpect(jsonPath("$.data.nickname").value("카카오유저"));

		// 3) /refresh: refresh 쿠키 → 새 access/refresh 회전 발급
		MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(new Cookie("refresh_token", refreshToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andReturn();
		assertThat(refreshResult.getResponse().getHeader("Set-Cookie")).contains("refresh_token=");
		String rotatedAccess = JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.data.accessToken");
		assertThat(rotatedAccess).isNotBlank();

		// 4) 온보딩: PUT 프로필 → profileCompleted=true
		mockMvc.perform(put("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"온보딩닉\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(userId))
				.andExpect(jsonPath("$.data.nickname").value("온보딩닉"))
				.andExpect(jsonPath("$.data.profileCompleted").value(true));

		// 5) 연동: 같은 카카오 재연동 → 본인 소유라 멱등(같은 userId)
		mockMvc.perform(post("/api/v1/auth/link/kakao")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"auth-code-2\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(userId))
				.andExpect(jsonPath("$.data.provider").value("kakao"));
	}

	@Test
	@DisplayName("허용되지 않은 redirectUri는 400 INVALID_REDIRECT_URI로 일관 응답")
	void 비정상_redirectUri_에러응답() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"x\",\"redirectUri\":\"https://evil.example.com\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.result").value("FAIL"))
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_REDIRECT_URI"));
	}

	/** "refresh_token=<jwt>; Max-Age=...; ..." 에서 토큰 값만 추출. */
	private static String extractRefreshToken(String setCookie) {
		String prefix = "refresh_token=";
		int start = prefix.length();
		int end = setCookie.indexOf(';');
		return end < 0 ? setCookie.substring(start) : setCookie.substring(start, end);
	}
}
