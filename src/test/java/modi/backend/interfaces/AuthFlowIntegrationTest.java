package modi.backend.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import modi.backend.infra.auth.NaverApi;

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

	@MockitoBean
	NaverApi naverApi;

	@Test
	@DisplayName("로그인 → /me → /refresh → 온보딩까지 정상 응답")
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

		// 2) /users/me: access_token 쿠키 → @Authentication 주입으로 내 프로필 (쿠키 인증 경로)
		mockMvc.perform(get("/api/v1/users/me").cookie(new Cookie("access_token", cookieAccessToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(userId))
				.andExpect(jsonPath("$.data.provider").value("kakao"))
				.andExpect(jsonPath("$.data.nickname").value("카카오유저"));

		// 2-1) /users/me: Bearer 헤더 폴백도 여전히 동작
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
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
	}

	@Test
	@DisplayName("게스트 로그인 → 소셜 없이 토큰 발급(provider=guest, email=null) + 그 토큰으로 로그인 전용 /users/me 접근")
	void 게스트_로그인_후_로그인전용_API_사용() throws Exception {
		// 1) 게스트 로그인: 소셜 인증 없이 임시 사용자 생성 + 자체 JWT 발급(가입 겸용)
		MvcResult guest = mockMvc.perform(post("/api/v1/auth/guest"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meta.result").value("SUCCESS"))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.user.provider").value("guest"))
				.andExpect(jsonPath("$.data.user.nickname").value("게스트"))
				.andExpect(jsonPath("$.data.user.email").value(nullValue()))
				.andExpect(jsonPath("$.data.user.profileCompleted").value(false))
				.andReturn();
		String body = guest.getResponse().getContentAsString();
		String accessToken = JsonPath.read(body, "$.data.accessToken");
		Integer userId = JsonPath.read(body, "$.data.user.userId");

		// access·refresh 모두 HttpOnly 쿠키로 내려간다(소셜 로그인과 동일 체계)
		assertThat(setCookie(guest, "access_token")).isNotNull().contains("HttpOnly");
		assertThat(setCookie(guest, "refresh_token")).isNotNull().contains("HttpOnly");

		// 2) 발급된 게스트 토큰으로 로그인 전용 API(/users/me) 정상 접근
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(userId))
				.andExpect(jsonPath("$.data.provider").value("guest"))
				.andExpect(jsonPath("$.data.nickname").value("게스트"));
	}

	@Test
	@DisplayName("게스트 로그인은 호출할 때마다 새 사용자를 생성한다(userId 상이)")
	void 게스트_로그인_매번_새_사용자() throws Exception {
		Integer first = JsonPath.read(mockMvc.perform(post("/api/v1/auth/guest"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.user.userId");
		Integer second = JsonPath.read(mockMvc.perform(post("/api/v1/auth/guest"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.user.userId");
		assertThat(first).isNotEqualTo(second);
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

		// 서버에서 refresh 폐기됨 → 같은 refresh 쿠키로 재발급 불가 (L3)
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refresh)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_REFRESH_TOKEN"));
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

	@Test
	@DisplayName("A2 기존 유저: 동일 (provider, providerUserId) 재로그인 → 같은 userId 재사용 + email 갱신")
	void A2_기존유저_재로그인() throws Exception {
		given(kakaoApi.getToken(any())).willReturn(Map.of("access_token", "kakao-access-token"));
		given(kakaoApi.getUserInfo(anyString())).willReturn(Map.of(
				"id", 8888001,
				"kakao_account", Map.of("email", "old@kakao.com", "profile", Map.of("nickname", "재로그인유저"))));
		MvcResult first = mockMvc.perform(post("/api/v1/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"c1\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		Integer firstUserId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.user.userId");

		// 같은 소셜 계정, email만 변경해 재로그인
		given(kakaoApi.getUserInfo(anyString())).willReturn(Map.of(
				"id", 8888001,
				"kakao_account", Map.of("email", "new@kakao.com", "profile", Map.of("nickname", "재로그인유저"))));
		mockMvc.perform(post("/api/v1/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"c2\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.userId").value(firstUserId)) // 신규 가입 없이 동일 유저
				.andExpect(jsonPath("$.data.user.email").value("new@kakao.com")); // email 최신화
	}

	@Test
	@DisplayName("A3 이메일 미동의(email 없음)로 로그인 → 200, user.email=null")
	void A3_이메일미동의_로그인() throws Exception {
		given(kakaoApi.getToken(any())).willReturn(Map.of("access_token", "kakao-access-token"));
		given(kakaoApi.getUserInfo(anyString())).willReturn(Map.of(
				"id", 8888002,
				"kakao_account", Map.of("profile", Map.of("nickname", "무이메일유저"))));
		mockMvc.perform(post("/api/v1/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"c\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.email").value(nullValue()));
	}

	@Test
	@DisplayName("A4 지원하지 않는 provider(facebook) → 400 UNSUPPORTED_PROVIDER")
	void A4_미지원_provider() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login/facebook")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"c\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("UNSUPPORTED_PROVIDER"));
	}

	@Test
	@DisplayName("A9 네이버 provider 로그인(response 중첩 구조 + state) → 200 + provider=naver, 연령대·출생연도 반영")
	void A9_네이버_로그인() throws Exception {
		given(naverApi.getToken(any())).willReturn(Map.of("access_token", "naver-access-token"));
		given(naverApi.getUserInfo(anyString())).willReturn(Map.of(
				"resultcode", "00",
				"message", "success",
				"response", Map.of(
						"id", "naver-sub-1",
						"email", "user@naver.com",
						"name", "네이버유저",
						"nickname", "네이버닉",
						"age", "20-29",
						"birthyear", "1998")));
		mockMvc.perform(post("/api/v1/auth/login/naver")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"ncode\",\"redirectUri\":\"" + REDIRECT_URI + "\",\"state\":\"nstate\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.provider").value("naver"))
				.andExpect(jsonPath("$.data.user.email").value("user@naver.com"))
				.andExpect(jsonPath("$.data.user.name").value("네이버유저"))
				.andExpect(jsonPath("$.data.user.ageGroup").value("TWENTIES"))
				.andExpect(jsonPath("$.data.user.birthYear").value(1998))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty());
	}

	@Test
	@DisplayName("A10 네이버 프로필 조회 실패(resultcode!=00, HTTP 200) → 502 OAUTH_COMMUNICATION_FAILED")
	void A10_네이버_프로필실패() throws Exception {
		given(naverApi.getToken(any())).willReturn(Map.of("access_token", "naver-access-token"));
		given(naverApi.getUserInfo(anyString())).willReturn(Map.of(
				"resultcode", "024", "message", "Authentication failed"));
		mockMvc.perform(post("/api/v1/auth/login/naver")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"ncode\",\"redirectUri\":\"" + REDIRECT_URI + "\",\"state\":\"nstate\"}"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.meta.errorCode").value("OAUTH_COMMUNICATION_FAILED"));
	}

	@Test
	@DisplayName("A6 소셜 토큰 교환 실패(access_token 없음) → 502 OAUTH_COMMUNICATION_FAILED")
	void A6_소셜통신실패() throws Exception {
		given(kakaoApi.getToken(any())).willReturn(Map.of()); // access_token 누락 → 교환 실패
		mockMvc.perform(post("/api/v1/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"c\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.meta.errorCode").value("OAUTH_COMMUNICATION_FAILED"));
	}

	@Test
	@DisplayName("R3 위조/무효 refresh 쿠키 → 401 INVALID_REFRESH_TOKEN")
	void R3_위조_refresh() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(new Cookie("refresh_token", "garbage.token.value")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	@DisplayName("L2 refresh 쿠키 없이 로그아웃 → 200 (멱등)")
	void L2_refresh없이_로그아웃() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meta.result").value("SUCCESS"));
	}

	@Test
	@DisplayName("카카오 동의항목(이름·연령대·출생연도) 신규 가입 시 로그인 응답 user + /users/me에 반영")
	void 카카오_이름_연령대_출생연도_반영() throws Exception {
		given(kakaoApi.getToken(any())).willReturn(Map.of("access_token", "kakao-access-token"));
		given(kakaoApi.getUserInfo(anyString())).willReturn(Map.of(
				"id", 8888007,
				"kakao_account", Map.of(
						"email", "age@kakao.com",
						"name", "홍길동",
						"age_range", "20~29",
						"birthyear", "1998",
						"profile", Map.of("nickname", "연령대유저"))));
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"c\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
				.andExpect(status().isOk())
				// 로그인 응답 user에 이름·연령대·출생연도가 함께 실려 온다
				.andExpect(jsonPath("$.data.user.name").value("홍길동"))
				.andExpect(jsonPath("$.data.user.ageGroup").value("TWENTIES"))
				.andExpect(jsonPath("$.data.user.birthYear").value(1998))
				.andExpect(jsonPath("$.data.user.email").value("age@kakao.com"))
				.andReturn();
		String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");

		// 소셜 동의항목이 도메인까지 반영됐는지 프로필 조회로도 확인
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.ageGroup").value("TWENTIES")) // age_range "20~29" → TWENTIES
				.andExpect(jsonPath("$.data.birthYear").value(1998));      // birthyear "1998" → 1998
	}

	@Test
	@DisplayName("R4 회전 후 옛 refresh 재사용 → 401 INVALID_REFRESH_TOKEN")
	void R4_회전후_옛refresh_무효() throws Exception {
		given(kakaoApi.getToken(any())).willReturn(Map.of("access_token", "kakao-access-token"));
		given(kakaoApi.getUserInfo(anyString())).willReturn(Map.of(
				"id", 8888006, "kakao_account", Map.of("profile", Map.of("nickname", "회전유저"))));
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"c\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String oldRefresh = cookieValue(setCookie(login, "refresh_token"));

		// 회전: 새 refresh 발급 (jti 덕분에 옛것과 반드시 다르다)
		MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(new Cookie("refresh_token", oldRefresh)))
				.andExpect(status().isOk())
				.andReturn();
		String newRefresh = cookieValue(setCookie(rotated, "refresh_token"));
		assertThat(newRefresh).isNotEqualTo(oldRefresh);

		// 옛 refresh 재사용 → 저장소의 현재 값과 불일치로 무효
		mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(new Cookie("refresh_token", oldRefresh)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_REFRESH_TOKEN"));
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
