package modi.backend.interfaces;

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

import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.auth.TokenProvider;
import modi.backend.domain.user.User;
import modi.backend.domain.user.UserRepository;
import modi.backend.infra.auth.KakaoApi;
import modi.backend.infra.user.UserJpaRepository;

/**
 * 유저 도메인(02_유저.md) API end-to-end 검증.
 * 외부 카카오 HTTP({@link KakaoApi})만 목으로 두고, 로그인으로 실제 토큰을 발급받아
 * GET /users/me · PUT /users/me/profile 를 실제 컨트롤러·DB(Testcontainers)로 태운다.
 * (프로젝트 컨벤션 우선: 성공 200, 프로필 수정은 PUT. tasteKeywords/stats는 스텁으로 []·0.)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class UserProfileIntegrationTest {

	private static final String REDIRECT_URI = "http://localhost:3000/login"; // application.yaml 화이트리스트

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TokenProvider tokenProvider;

	@Autowired
	UserRepository userRepository;

	@Autowired
	UserJpaRepository userJpaRepository;

	@MockitoBean
	KakaoApi kakaoApi;

	/** 카카오 로그인으로 실제 access 토큰을 발급받는다(providerUserId로 유저 격리). */
	private String loginAndGetAccessToken(long providerUserId, String nickname) throws Exception {
		given(kakaoApi.getToken(any())).willReturn(Map.of("access_token", "kakao-access-token"));
		given(kakaoApi.getUserInfo(anyString())).willReturn(Map.of(
				"id", providerUserId,
				"kakao_account", Map.of("profile", Map.of("nickname", nickname))));
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"auth-code\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
	}

	@Test
	@DisplayName("GET /users/me — 로그인 사용자, 200 + 프로필/취향/통계 shape(취향·통계는 스텁)")
	void 프로필_조회_정상() throws Exception {
		// Arrange
		String accessToken = loginAndGetAccessToken(1111111L, "조회유저");

		// Act & Assert
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meta.result").value("SUCCESS"))
				.andExpect(jsonPath("$.data.userId").isNumber())
				.andExpect(jsonPath("$.data.nickname").value("조회유저"))
				.andExpect(jsonPath("$.data.provider").value("kakao"))
				// 가입 직후: profileImageUrl·거주지역 미입력 → null, ageGroup UNSPECIFIED → null
				.andExpect(jsonPath("$.data.profileImageUrl").doesNotExist())
				.andExpect(jsonPath("$.data.ageGroup").doesNotExist())
				.andExpect(jsonPath("$.data.residenceRegion").doesNotExist())
				.andExpect(jsonPath("$.data.residenceDistrict").doesNotExist())
				// 스텁: 취향 키워드 빈 배열, 통계 0
				.andExpect(jsonPath("$.data.tasteKeywords").isArray())
				.andExpect(jsonPath("$.data.tasteKeywords").isEmpty())
				.andExpect(jsonPath("$.data.stats.recordCount").value(0))
				.andExpect(jsonPath("$.data.stats.exhibitionCount").value(0))
				.andExpect(jsonPath("$.data.stats.bookmarkCount").value(0));
	}

	@Test
	@DisplayName("PUT /users/me/profile — 닉네임만 변경, 200 + 닉네임만 갱신 나머지 유지")
	void 프로필_닉네임만_수정() throws Exception {
		// Arrange
		String accessToken = loginAndGetAccessToken(2222222L, "원래닉");

		// Act & Assert: 닉네임만 전달
		mockMvc.perform(put("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"바뀐닉\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nickname").value("바뀐닉"))
				.andExpect(jsonPath("$.data.residenceRegion").doesNotExist());

		// GET로 다시 확인: 나머지 필드 유지(미입력 그대로 null)
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nickname").value("바뀐닉"))
				.andExpect(jsonPath("$.data.ageGroup").doesNotExist());
	}

	@Test
	@DisplayName("PUT /users/me/profile — ageGroup·residenceRegion·residenceDistrict 함께 입력, 200 + 모두 갱신")
	void 프로필_전체필드_수정() throws Exception {
		// Arrange
		String accessToken = loginAndGetAccessToken(3333333L, "전체유저");

		// Act
		mockMvc.perform(put("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nickname":"완성유저","profileImageUrl":"https://img.example/p.png",
								 "ageGroup":"TWENTIES","residenceRegion":"SEOUL","residenceDistrict":"강남구"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.profileCompleted").value(true));

		// Assert: GET로 모두 반영 확인
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nickname").value("완성유저"))
				.andExpect(jsonPath("$.data.profileImageUrl").value("https://img.example/p.png"))
				.andExpect(jsonPath("$.data.ageGroup").value("TWENTIES"))
				.andExpect(jsonPath("$.data.residenceRegion").value("SEOUL"))
				.andExpect(jsonPath("$.data.residenceDistrict").value("강남구"));
	}

	@Test
	@DisplayName("PUT /users/me/profile — 빈 닉네임, 400 INVALID_NICKNAME")
	void 프로필_빈닉네임_400() throws Exception {
		// Arrange
		String accessToken = loginAndGetAccessToken(4444444L, "닉유저");

		// Act & Assert
		mockMvc.perform(put("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.result").value("FAIL"))
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_NICKNAME"));
	}

	@Test
	@DisplayName("PUT /users/me/profile — residenceDistrict만 입력(region 없음), 400 INVALID_INPUT")
	void 프로필_지역없이_구만_400() throws Exception {
		// Arrange
		String accessToken = loginAndGetAccessToken(5555555L, "지역유저");

		// Act & Assert
		mockMvc.perform(put("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"residenceDistrict\":\"강남구\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.result").value("FAIL"))
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("미인증 — GET/PUT 모두 401 NO_ACCESS_TOKEN")
	void 미인증_401() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.meta.errorCode").value("NO_ACCESS_TOKEN"));
		mockMvc.perform(put("/api/v1/users/me/profile")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"x\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.meta.errorCode").value("NO_ACCESS_TOKEN"));
	}

	@Test
	@DisplayName("P4 닉네임 21자 초과 → 400 INVALID_NICKNAME")
	void P4_닉네임_길이초과() throws Exception {
		String accessToken = loginAndGetAccessToken(6666601L, "길이유저");

		mockMvc.perform(put("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"" + "가".repeat(21) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_NICKNAME"));
	}

	@Test
	@DisplayName("P6 잘못된 ageGroup enum 문자열 → 400 INVALID_INPUT")
	void P6_잘못된_enum() throws Exception {
		String accessToken = loginAndGetAccessToken(6666602L, "이넘유저");

		mockMvc.perform(put("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ageGroup\":\"NOT_A_REAL_GROUP\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("P7 빈 바디(모든 필드 null) → 200, 기존 필드 유지 + profileCompleted=true")
	void P7_빈바디_부분갱신() throws Exception {
		String accessToken = loginAndGetAccessToken(6666603L, "빈바디유저");

		mockMvc.perform(put("/api/v1/users/me/profile")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nickname").value("빈바디유저")) // 미전달 → 유지
				.andExpect(jsonPath("$.data.profileCompleted").value(true));
	}

	@Test
	@DisplayName("Z2 위조/무효 access 토큰 → 401 INVALID_ACCESS_TOKEN")
	void Z2_위조_access() throws Exception {
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer garbage.token.value"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	@DisplayName("Z3 refresh 토큰을 access 자리에 사용(type 불일치) → 401 INVALID_ACCESS_TOKEN")
	void Z3_refresh를_access자리() throws Exception {
		User user = userRepository.save(User.createFromSocial("타입불일치유저"));
		String refreshToken = tokenProvider.issue(user, "kakao").refreshToken();

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + refreshToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_ACCESS_TOKEN"));
	}

	@Test
	@DisplayName("M2 토큰의 userId가 DB에 없음(삭제됨) → 404 USER_NOT_FOUND")
	void M2_없는유저_404() throws Exception {
		// 패턴 A: 유저 저장 → 토큰 발급 → 유저 삭제(토큰만 유효한 상태 재현)
		User user = userRepository.save(User.createFromSocial("삭제될유저"));
		String accessToken = tokenProvider.issue(user, "kakao").accessToken();
		userJpaRepository.deleteById(user.getId());

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.meta.errorCode").value("USER_NOT_FOUND"));
	}
}
