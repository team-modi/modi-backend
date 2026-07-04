package modi.backend.interfaces.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import modi.backend.TestcontainersConfiguration;
import modi.backend.application.exhibition.ExhibitionFacade;
import modi.backend.domain.auth.TokenProvider;
import modi.backend.domain.exhibition.CatalogExhibitionData;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.domain.exhibition.ExhibitionRepository;
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

	@Autowired
	ExhibitionRepository exhibitionRepository;

	@Autowired
	ExhibitionFacade exhibitionFacade;

	// 스냅샷 독립성 e2e(Task 14)에서만 사용 — CATALOG 재동기화로 원본 전시 제목을 실제로 바꿔보기 위해 수집 포트를 목으로 둔다.
	@MockitoBean
	ExhibitionCatalogClient catalogClient;

	// 기록 API는 @Authentication 으로 실제 로그인 사용자를 받는다(X-User-Id 스텁 폐기).
	// 테스트는 유저를 저장한 뒤 그 유저의 access 토큰을 Bearer 로 보내 인증한다.
	private String bearerUser1;
	private String bearerUser2;

	// RecordService.create가 ExhibitionFacade.getDetail로 실제 전시를 조회해 스냅샷을 박제하므로
	// (14 Task 12), 기록 생성 요청에는 DB에 실재하는 전시 id를 사용해야 한다(임의의 51 고정값 대신).
	private Long exhibitionId;

	@BeforeEach
	void setUp() {
		recordRepository.deleteAll();
		User u1 = userRepository.save(User.createFromSocial("user1"));
		User u2 = userRepository.save(User.createFromSocial("user2"));
		bearerUser1 = "Bearer " + tokenProvider.issue(u1, "kakao").accessToken();
		bearerUser2 = "Bearer " + tokenProvider.issue(u2, "kakao").accessToken();

		Exhibition exhibition = exhibitionRepository.save(Exhibition.createCatalog(
				"RECORD-TEST-" + System.nanoTime(), "모네전", "예술의전당", null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null));
		exhibitionId = exhibition.getId();
	}

	@Test
	void create_record_and_get_detail() throws Exception {
		String response = mockMvc.perform(post("/api/v1/records")
						.header("Authorization", bearerUser1)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "exhibitionId": %d,
								  "writeMode": "DIRECT",
								  "viewedAt": "2020-06-28",
								  "content": "색이 따뜻해서 한참 서 있었다.",
								  "emotionCodes": ["강렬한", "서정적인"],
								  "media": [
								    {
								      "type": "PHOTO",
								      "url": "https://example.com/image.jpg",
								      "sortOrder": 0,
								      "sizeBytes": 1048576
								    }
								  ]
								}
								""".formatted(exhibitionId)))
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
				.andExpect(jsonPath("$.data.exhibitionId").value(exhibitionId.intValue()))
				.andExpect(jsonPath("$.data.content").value("색이 따뜻해서 한참 서 있었다."))
				.andExpect(jsonPath("$.data.emotionCodes", hasSize(2)))
				.andExpect(jsonPath("$.data.media[0].type").value("PHOTO"))
				.andExpect(jsonPath("$.data.exhibitionTitle").value("모네전"))
				.andExpect(jsonPath("$.data.exhibitionPlace").value("예술의전당"));
	}

	@Test
	void get_visited_exhibitions_list() throws Exception {
		createRecord(bearerUser1, "모네 전시의 색감이 좋았다.", "MOVED");
		createRecord(bearerUser2, "다른 사용자의 기록.", "MOVED");

		mockMvc.perform(get("/api/v1/records/exhibitions/visited")
						.header("Authorization", bearerUser1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content", hasSize(1)))
				.andExpect(jsonPath("$.data.content[0].exhibitionTitle").value("모네전"))
				.andExpect(jsonPath("$.data.content[0].exhibitionPlace").value("예술의전당"));
	}

	@Test
	@DisplayName("기록 스냅샷 독립성(e2e) — CATALOG 재동기화로 원본 전시 제목이 실제로 바뀌어도 다녀온 목록의 스냅샷 제목은 기록 작성 시점 값으로 유지된다")
	void 기록_스냅샷은_전시_원본_재동기화와_독립적이다() throws Exception {
		LocalDate today = LocalDate.now();
		String externalId = "CAT-SNAPSHOT-E2E-" + System.nanoTime();
		String originalTitle = "스냅샷 원본전시-" + System.nanoTime();
		String mutatedTitle = "스냅샷 변경후전시-" + System.nanoTime();

		// 1) 전시 동기화(목) — 원본 제목으로 CATALOG 최초 적재
		given(catalogClient.fetchAll()).willReturn(List.of(
				new CatalogExhibitionData(externalId, originalTitle, "스냅샷 갤러리", today.minusDays(5),
						today.plusDays(25), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING,
						"https://poster/snapshot.jpg", null, "기관", null, null, null, "전시", "서울")));
		exhibitionFacade.syncCatalog();
		Long catalogExhibitionId = exhibitionRepository.findByExternalId(externalId).orElseThrow().getId();

		// 2) 기록 작성 — RecordService.create가 이 시점의 전시 제목을 스냅샷으로 박제한다
		String createResponse = mockMvc.perform(post("/api/v1/records")
						.header("Authorization", bearerUser1)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "exhibitionId": %d,
								  "writeMode": "DIRECT",
								  "viewedAt": "2020-06-28",
								  "content": "스냅샷 독립성 검증용 기록",
								  "emotionCodes": ["MOVED"],
								  "media": []
								}
								""".formatted(catalogExhibitionId)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String recordId = createResponse.replaceAll("(?s).*\"recordId\":(\\d+).*", "$1");

		// 3) 작성 직후 다녀온 목록 — 스냅샷 제목이 원본 제목과 일치
		mockMvc.perform(get("/api/v1/records/exhibitions/visited")
						.header("Authorization", bearerUser1)
						.param("exhibitionId", String.valueOf(catalogExhibitionId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.recordId==" + recordId + ")].exhibitionTitle")
						.value(hasItem(originalTitle)));

		// 4) 같은 externalId를 다른 제목으로 재동기화 — 실제 운영에서 매시 배치가 하는 일과 동일(ExhibitionFacade.refresh)
		given(catalogClient.fetchAll()).willReturn(List.of(
				new CatalogExhibitionData(externalId, mutatedTitle, "스냅샷 갤러리 이전", today.minusDays(5),
						today.plusDays(25), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING,
						"https://poster/mutated.jpg", null, "기관", null, null, null, "전시", "서울")));
		exhibitionFacade.syncCatalog();

		// 원본 전시 행의 제목이 실제로 바뀌었음을 먼저 확인한다 — 이래야 다음 단계가 "아무것도 안 해도 통과"하는 가짜 검증이 아니게 된다.
		Exhibition mutated = exhibitionRepository.findByExternalId(externalId).orElseThrow();
		assertThat(mutated.getTitle()).isEqualTo(mutatedTitle);

		// 5) 재동기화 이후에도 다녀온 목록의 스냅샷 제목은 여전히 "기록 작성 시점" 제목 — 전시 원본 변경과 무관하다.
		mockMvc.perform(get("/api/v1/records/exhibitions/visited")
						.header("Authorization", bearerUser1)
						.param("exhibitionId", String.valueOf(catalogExhibitionId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.recordId==" + recordId + ")].exhibitionTitle")
						.value(hasItem(originalTitle)))
				.andExpect(jsonPath("$.data.content[?(@.recordId==" + recordId + ")].exhibitionTitle")
						.value(not(hasItem(mutatedTitle))));
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
								  "emotionCodes": ["서정적인"],
								  "media": []
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").value("수정한 감상"))
				.andExpect(jsonPath("$.data.emotionCodes[0]").value("서정적인"));

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
								  "exhibitionId": %d,
								  "writeMode": "DIRECT",
								  "viewedAt": "2020-06-28",
								  "content": "%s",
								  "emotionCodes": ["%s"],
								  "media": []
								}
								""".formatted(exhibitionId, content, emotion)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return response.replaceAll("(?s).*\"recordId\":(\\d+).*", "$1");
	}
}
