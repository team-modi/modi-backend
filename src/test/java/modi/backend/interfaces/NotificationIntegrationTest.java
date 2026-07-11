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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.notification.Notification;
import modi.backend.domain.notification.NotificationRepository;
import modi.backend.domain.notification.NotificationType;
import modi.backend.infra.auth.KakaoApi;

/**
 * 알림 도메인(09_알림.md) API end-to-end 검증.
 * 외부 카카오 HTTP({@link KakaoApi})만 목으로 두고, 로그인으로 실제 토큰을 발급받아
 * GET /notifications · PUT /notifications/{id}/read 를 실제 컨트롤러·DB(Testcontainers)로 태운다.
 * 알림은 로그인 유저 id로 {@link NotificationRepository}에 직접 시드한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {

	private static final String REDIRECT_URI = "http://localhost:3000/login"; // application.yaml 화이트리스트

	@Autowired
	MockMvc mockMvc;

	@Autowired
	NotificationRepository notificationRepository;

	@MockitoBean
	KakaoApi kakaoApi;

	/** 카카오 로그인으로 실제 access 토큰을 발급받는다(providerUserId로 유저 격리). */
	private String loginAndGetAccessToken(long providerUserId, String nickname) throws Exception {
		given(kakaoApi.getToken(any())).willReturn(Map.of("access_token", "kakao-access-token"));
		given(kakaoApi.getUserInfo(anyString())).willReturn(Map.of(
				"id", providerUserId,
				"kakao_account", Map.of("profile", Map.of("nickname", nickname))));
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login/kakao")
						.contentType("application/json")
						.content("{\"code\":\"auth-code\",\"redirectUri\":\"" + REDIRECT_URI + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
	}

	/** access 토큰으로 내 userId를 조회한다(GET /users/me). */
	private long userIdOf(String accessToken) throws Exception {
		MvcResult me = mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andReturn();
		Number userId = JsonPath.read(me.getResponse().getContentAsString(), "$.data.userId");
		return userId.longValue();
	}

	@Test
	@DisplayName("GET /notifications — 시드 알림을 최신순으로, 필드·totalCount와 함께 반환")
	void 목록_최신순_조회() throws Exception {
		// Arrange
		String token = loginAndGetAccessToken(9100001L, "목록유저");
		long userId = userIdOf(token);
		notificationRepository.save(Notification.create(userId, NotificationType.NOTICE, "오래된공지", "본문1", null));
		notificationRepository.save(Notification.create(userId, NotificationType.REMIND, "중간리마인드", "본문2", 5L));
		notificationRepository.save(Notification.create(userId, NotificationType.REMIND, "최신리마인드", "본문3", 9L));

		// Act & Assert — 최신(마지막 저장)이 content[0]
		mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meta.result").value("SUCCESS"))
				.andExpect(jsonPath("$.data.totalCount").value(3))
				.andExpect(jsonPath("$.data.hasNext").value(false))
				.andExpect(jsonPath("$.data.nextCursor").doesNotExist())
				.andExpect(jsonPath("$.data.content.length()").value(3))
				.andExpect(jsonPath("$.data.content[0].title").value("최신리마인드"))
				.andExpect(jsonPath("$.data.content[0].type").value("REMIND"))
				.andExpect(jsonPath("$.data.content[0].targetId").value(9))
				.andExpect(jsonPath("$.data.content[0].read").value(false))
				.andExpect(jsonPath("$.data.content[0].notificationId").isNumber())
				.andExpect(jsonPath("$.data.content[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$.data.content[1].title").value("중간리마인드"))
				.andExpect(jsonPath("$.data.content[2].title").value("오래된공지"))
				.andExpect(jsonPath("$.data.content[2].type").value("NOTICE"))
				.andExpect(jsonPath("$.data.content[2].targetId").doesNotExist());
	}

	@Test
	@DisplayName("GET /notifications — 알림 없으면 빈 목록(content [], hasNext false, totalCount 0)")
	void 목록_빈결과() throws Exception {
		String token = loginAndGetAccessToken(9100002L, "빈유저");

		mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(0))
				.andExpect(jsonPath("$.data.hasNext").value(false))
				.andExpect(jsonPath("$.data.nextCursor").doesNotExist())
				.andExpect(jsonPath("$.data.totalCount").value(0));
	}

	@Test
	@DisplayName("GET /notifications — size=1 커서 페이징으로 3건을 중복·누락 없이 순회, 마지막 nextCursor null")
	void 커서_페이징_순회() throws Exception {
		// Arrange
		String token = loginAndGetAccessToken(9100003L, "페이징유저");
		long userId = userIdOf(token);
		notificationRepository.save(Notification.create(userId, NotificationType.NOTICE, "A", "본문", null));
		notificationRepository.save(Notification.create(userId, NotificationType.NOTICE, "B", "본문", null));
		notificationRepository.save(Notification.create(userId, NotificationType.NOTICE, "C", "본문", null)); // 최신

		// Page 1
		MvcResult p1 = mockMvc.perform(get("/api/v1/notifications?size=1").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].title").value("C"))
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andExpect(jsonPath("$.data.totalCount").value(3))
				.andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
				.andReturn();
		String cursor1 = JsonPath.read(p1.getResponse().getContentAsString(), "$.data.nextCursor");

		// Page 2
		MvcResult p2 = mockMvc.perform(get("/api/v1/notifications?size=1&cursor=" + cursor1)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].title").value("B"))
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
				.andReturn();
		String cursor2 = JsonPath.read(p2.getResponse().getContentAsString(), "$.data.nextCursor");

		// Page 3 (last)
		mockMvc.perform(get("/api/v1/notifications?size=1&cursor=" + cursor2)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].title").value("A"))
				.andExpect(jsonPath("$.data.hasNext").value(false))
				.andExpect(jsonPath("$.data.nextCursor").doesNotExist());
	}

	@Test
	@DisplayName("GET /notifications?type= — 종류 필터: 해당 type만, totalCount도 필터 기준")
	void 목록_type필터() throws Exception {
		// Arrange — 세 종류를 모두 시드
		String token = loginAndGetAccessToken(9100009L, "필터유저");
		long userId = userIdOf(token);
		notificationRepository.save(Notification.create(userId, NotificationType.NOTICE, "공지", "본문", null));
		notificationRepository.save(Notification.create(userId, NotificationType.REMIND, "리마인드", "본문", 5L));
		notificationRepository.save(Notification.create(userId, NotificationType.EXHIBITION, "전시", "본문", 7L));

		// Act & Assert — EXHIBITION만
		mockMvc.perform(get("/api/v1/notifications?type=EXHIBITION").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].type").value("EXHIBITION"))
				.andExpect(jsonPath("$.data.content[0].targetId").value(7))
				.andExpect(jsonPath("$.data.totalCount").value(1));

		// 미지정이면 전체
		mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(3))
				.andExpect(jsonPath("$.data.totalCount").value(3));
	}

	@Test
	@DisplayName("GET /notifications?type=잘못된값 → 400 INVALID_INPUT")
	void 잘못된_type_400() throws Exception {
		String token = loginAndGetAccessToken(9100010L, "타입유저");

		mockMvc.perform(get("/api/v1/notifications?type=WRONG").header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("GET /notifications — 손상된 커서 → 400 INVALID_CURSOR")
	void 손상커서_400() throws Exception {
		String token = loginAndGetAccessToken(9100004L, "커서유저");

		mockMvc.perform(get("/api/v1/notifications?cursor=not-a-valid-cursor")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_CURSOR"));
	}

	@Test
	@DisplayName("PUT /notifications/{id}/read — 읽음 처리 후 GET 재조회 시 read=true")
	void 읽음처리_반영() throws Exception {
		// Arrange
		String token = loginAndGetAccessToken(9100005L, "읽음유저");
		long userId = userIdOf(token);
		Notification saved = notificationRepository.save(
				Notification.create(userId, NotificationType.REMIND, "읽을알림", "본문", 3L));

		// Act
		mockMvc.perform(put("/api/v1/notifications/" + saved.getId() + "/read")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.notificationId").value(saved.getId()))
				.andExpect(jsonPath("$.data.read").value(true));

		// Assert — 다시 조회하면 read=true (멱등 재호출도 200)
		mockMvc.perform(put("/api/v1/notifications/" + saved.getId() + "/read")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.read").value(true));

		mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].read").value(true));
	}

	@Test
	@DisplayName("PUT /notifications/{id}/read — 타인 알림 → 404 NOTIFICATION_NOT_FOUND")
	void 타인알림_404() throws Exception {
		// Arrange — 소유자 B의 알림을 A가 읽으려 시도
		String ownerToken = loginAndGetAccessToken(9100006L, "소유자");
		long ownerId = userIdOf(ownerToken);
		Notification others = notificationRepository.save(
				Notification.create(ownerId, NotificationType.NOTICE, "남의알림", "본문", null));

		String attackerToken = loginAndGetAccessToken(9100007L, "침입자");

		// Act & Assert
		mockMvc.perform(put("/api/v1/notifications/" + others.getId() + "/read")
						.header("Authorization", "Bearer " + attackerToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.meta.result").value("FAIL"))
				.andExpect(jsonPath("$.meta.errorCode").value("NOTIFICATION_NOT_FOUND"));
	}

	@Test
	@DisplayName("PUT /notifications/{id}/read — 없는 알림 → 404 NOTIFICATION_NOT_FOUND")
	void 없는알림_404() throws Exception {
		String token = loginAndGetAccessToken(9100008L, "없는유저");

		mockMvc.perform(put("/api/v1/notifications/99999999/read")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.meta.errorCode").value("NOTIFICATION_NOT_FOUND"));
	}

	@Test
	@DisplayName("미인증 — GET/PUT 모두 401")
	void 미인증_401() throws Exception {
		mockMvc.perform(get("/api/v1/notifications"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.meta.errorCode").value("NO_ACCESS_TOKEN"));
		mockMvc.perform(put("/api/v1/notifications/1/read"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.meta.errorCode").value("NO_ACCESS_TOKEN"));
	}
}
