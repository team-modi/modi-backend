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

		// 1) 로그인: code → 자체 JWT 발급. access·refresh 모두 HttpOnly 쿠키(access는 본문에도 호환용).
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

		// access·refresh 둘 다 HttpOnly 쿠키로 내려간다(Set-Cookie 헤더 직접 검증)
		String accessCookie = setCookie(loginResult, "access_token");
		String refreshCookie = setCookie(loginResult, "refresh_token");
		assertThat(accessCookie).isNotNull().contains("HttpOnly");
		assertThat(refreshCookie).isNotNull().contains("HttpOnly");
		String cookieAccessToken = cookieValue(accessCookie);
		String refreshToken = cookieValue(refreshCookie);
		assertThat(cookieAccessToken).isEqualTo(accessToken);
		assertThat(refreshToken).isNotBlank();

		// 2) /me: access_token 쿠키 → @Authentication 주입으로 내 정보 (쿠키 인증 경로)
		mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("access_token", cookieAccessToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(userId))
				.andExpect(jsonPath("$.data.provider").value("kakao"))
				.andExpect(jsonPath("$.data.nickname").value("카카오유저"));

		// 2-1) /me: Bearer 헤더 폴백도 여전히 동작
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(userId));

		// 3) /refresh: refresh 쿠키 → 새 access/refresh 회전 발급
		MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(new Cookie("refresh_token", refreshToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andReturn();
		assertThat(setCookie(refreshResult, "access_token")).isNotNull().contains("HttpOnly");
		assertThat(setCookie(refreshResult, "refresh_token")).isNotNull().contains("HttpOnly");
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
	@DisplayName("로그아웃 → access·refresh 쿠키 만료(Max-Age=0) + refresh 폐기로 재발급 불가")
	void 로그아웃_쿠키만료_refresh폐기() throws Exception {
		given(kakaoApi.getToken(any())).willReturn(Map.of("access_token", "kakao-access-token"));
		given(kakaoApi.getUserInfo(anyString())).willReturn(Map.of(
				"id", 7654321,
				"kakao_account", Map.of("profile", Map.of("nickname", "로그아웃유저"))));

		MvcResult login = mockMvc.perform(post("/api/v1/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"c\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String refresh = cookieValue(setCookie(login, "refresh_token"));

		// 로그아웃: 두 쿠키 모두 Max-Age=0 으로 만료
		MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
						.cookie(new Cookie("refresh_token", refresh)))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(setCookie(logout, "access_token")).contains("Max-Age=0");
		assertThat(setCookie(logout, "refresh_token")).contains("Max-Age=0");

		// 서버에서 refresh 폐기됨 → 같은 refresh 쿠키로 재발급 불가
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refresh)))
				.andExpect(status().isUnauthorized());
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

	/** 여러 Set-Cookie 헤더 중 주어진 이름으로 시작하는 것을 찾는다(없으면 null). */
	private static String setCookie(MvcResult result, String name) {
		return result.getResponse().getHeaders("Set-Cookie").stream()
				.filter(c -> c.startsWith(name + "="))
				.findFirst().orElse(null);
	}

	/** "name=<value>; Max-Age=...; ..." 에서 value만 추출. */
	private static String cookieValue(String setCookie) {
		int start = setCookie.indexOf('=') + 1;
		int end = setCookie.indexOf(';');
		return end < 0 ? setCookie.substring(start) : setCookie.substring(start, end);
	}
}
