package modi.backend.interfaces.record;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.auth.TokenProvider;
import modi.backend.domain.user.User;
import modi.backend.domain.user.UserRepository;
import modi.backend.infra.record.RecordJpaRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RecordV1ControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	RecordJpaRepository recordRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	TokenProvider tokenProvider;

	// 기록 API는 @Authentication 으로 실제 로그인 사용자를 받는다(X-User-Id 스텁 폐기).
	// 테스트는 유저를 저장한 뒤 그 유저의 access 토큰을 Bearer 로 보내 인증한다.
	private String bearerUser1;
	private String bearerUser2;

	@BeforeEach
	void setUp() {
		recordRepository.deleteAll();
		User u1 = userRepository.save(User.createFromSocial("user1"));
		User u2 = userRepository.save(User.createFromSocial("user2"));
		bearerUser1 = "Bearer " + tokenProvider.issue(u1, "kakao").accessToken();
		bearerUser2 = "Bearer " + tokenProvider.issue(u2, "kakao").accessToken();
	}

	@Test
	void create_record_and_get_detail() throws Exception {
		String response = mockMvc.perform(post("/api/v1/records")
						.header("Authorization", bearerUser1)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "exhibitionId": 51,
								  "writeMode": "AI",
								  "viewedAt": "2020-06-28",
								  "content": "색이 따뜻해서 한참 서 있었다.",
								  "emotionCodes": ["MOVED", "CALM"],
								  "userKeywords": ["색감"],
								  "aiKeywords": ["따뜻한 색감", "위로"],
								  "aiSummary": "평온하고 뭉클한 관람.",
								  "representativeEmotion": "MOVED",
								  "cardPhrase": "그날, 색이 나를 붙잡았다",
								  "media": [
								    {
								      "type": "PHOTO",
								      "url": "https://example.com/image.jpg",
								      "sortOrder": 0,
								      "sizeBytes": 1048576
								    }
								  ]
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.meta.result").value("SUCCESS"))
				.andExpect(jsonPath("$.data.recordId").exists())
				.andExpect(jsonPath("$.data.aiStatus").value("READY"))
				.andReturn()
				.getResponse()
				.getContentAsString();

		String recordId = response.replaceAll("(?s).*\"recordId\":(\\d+).*", "$1");

		mockMvc.perform(get("/api/v1/records/{recordId}", recordId)
						.header("Authorization", bearerUser1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.exhibitionId").value(51))
				.andExpect(jsonPath("$.data.content").value("색이 따뜻해서 한참 서 있었다."))
				.andExpect(jsonPath("$.data.emotionCodes", hasSize(2)))
				.andExpect(jsonPath("$.data.aiKeywords", hasSize(2)))
				.andExpect(jsonPath("$.data.media[0].type").value("PHOTO"));
	}

	@Test
	void search_records_with_keyword_and_emotion() throws Exception {
		createRecord(bearerUser1, "모네 전시의 색감이 좋았다.", "MOVED");
		createRecord(bearerUser1, "조용한 조각 전시였다.", "CALM");
		createRecord(bearerUser2, "모네를 다른 사용자가 기록했다.", "MOVED");

		mockMvc.perform(get("/api/v1/records")
						.header("Authorization", bearerUser1)
						.param("keyword", "모네")
						.param("emotion", "MOVED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content", hasSize(1)))
				.andExpect(jsonPath("$.data.content[0].recordId").exists());
	}

	@Test
	void update_and_delete_record() throws Exception {
		String recordId = createRecord(bearerUser1, "처음 감상", "MOVED");

		mockMvc.perform(put("/api/v1/records/{recordId}", recordId)
						.header("Authorization", bearerUser1)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "viewedAt": "2020-06-29",
								  "content": "수정한 감상",
								  "emotionCodes": ["CALM"],
								  "userKeywords": ["수정"],
								  "aiKeywords": [],
								  "aiSummary": "수정된 요약",
								  "representativeEmotion": "CALM",
								  "cardPhrase": "다시 남긴 여운",
								  "media": []
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").value("수정한 감상"))
				.andExpect(jsonPath("$.data.emotionCodes[0]").value("CALM"));

		mockMvc.perform(delete("/api/v1/records/{recordId}", recordId)
						.header("Authorization", bearerUser1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meta.result").value("SUCCESS"));

		mockMvc.perform(get("/api/v1/records/{recordId}", recordId)
						.header("Authorization", bearerUser1))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.meta.errorCode").value("NOT_FOUND"));
	}

	@Test
	void reject_future_viewed_at() throws Exception {
		mockMvc.perform(post("/api/v1/records")
						.header("Authorization", bearerUser1)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "exhibitionId": 51,
								  "writeMode": "DIRECT",
								  "viewedAt": "2999-01-01",
								  "content": "미래 관람 기록",
								  "emotionCodes": ["MOVED"],
								  "media": []
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
	}

	@Test
	void reject_video_over_100mb() throws Exception {
		mockMvc.perform(post("/api/v1/records")
						.header("Authorization", bearerUser1)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "exhibitionId": 51,
								  "writeMode": "DIRECT",
								  "viewedAt": "2020-06-28",
								  "content": "영상이 큰 기록",
								  "emotionCodes": ["MOVED"],
								  "media": [
								    {
								      "type": "VIDEO",
								      "url": "https://example.com/video.mp4",
								      "sortOrder": 0,
								      "sizeBytes": 104857601
								    }
								  ]
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_MEDIA"));
	}

	@Test
	void reject_request_without_access_token() throws Exception {
		mockMvc.perform(get("/api/v1/records"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.meta.errorCode").value("NO_ACCESS_TOKEN"));
	}

	private String createRecord(String bearer, String content, String emotion) throws Exception {
		String response = mockMvc.perform(post("/api/v1/records")
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "exhibitionId": 51,
								  "writeMode": "DIRECT",
								  "viewedAt": "2020-06-28",
								  "content": "%s",
								  "emotionCodes": ["%s"],
								  "userKeywords": ["모네"],
								  "media": []
								}
								""".formatted(content, emotion)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return response.replaceAll("(?s).*\"recordId\":(\\d+).*", "$1");
	}
}
