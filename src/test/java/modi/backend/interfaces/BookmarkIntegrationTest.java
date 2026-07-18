package modi.backend.interfaces;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
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
import modi.backend.domain.exhibition.catalog.Exhibition;
import modi.backend.domain.exhibition.catalog.ExhibitionCategory;
import modi.backend.domain.exhibition.catalog.ExhibitionRegion;
import modi.backend.domain.exhibition.catalog.ExhibitionRepository;
import modi.backend.domain.venue.Venue;
import modi.backend.domain.venue.VenueRepository;
import modi.backend.infra.auth.KakaoApi;

/**
 * 관심 전시(북마크) API end-to-end 검증(북마크 6.1~6.3) + 전시 배너(5.1)·직접추가 venueId·genre(5.4).
 * 외부 카카오 HTTP({@link KakaoApi})만 목으로 두고 로그인으로 실제 토큰을 발급, 컨트롤러·Facade·Entity·DB를 실제로 태운다.
 * 북마크 목록은 사용자별로 격리되므로 테스트마다 고유 providerUserId로 표본을 분리한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookmarkIntegrationTest {

	private static final String REDIRECT_URI = "http://localhost:3000/login";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ExhibitionRepository exhibitionRepository;

	@Autowired
	modi.backend.domain.exhibition.catalog.ExhibitionPlaceRepository exhibitionPlaceRepository;

	@Autowired
	modi.backend.infra.exhibition.catalog.ExhibitionDetailJpaRepository exhibitionDetailRepository;

	@Autowired
	VenueRepository venueRepository;

	@MockitoBean
	KakaoApi kakaoApi;

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

	/** 표본 CATALOG 적재(기간·조회수 제어). */
	private Long saveCatalog(String externalId, String title, LocalDate startDate, LocalDate endDate, int views) {
		// 전시장은 공유(N:1) — 배너·목록의 place가 "표본 장소"로 나오게 resolve-or-create로 하나를 재사용한다.
		Long placeId = modi.backend.domain.exhibition.catalog.ExhibitionTestFactory.placeId(
				exhibitionPlaceRepository, "표본 장소", ExhibitionRegion.SEOUL);
		Exhibition e = exhibitionRepository.save(Exhibition.createCatalog(externalId, title, placeId, startDate,
				endDate, ExhibitionCategory.PAINTING, "https://poster/" + externalId + ".jpg", null, "기관"));
		exhibitionDetailRepository.save(modi.backend.domain.exhibition.catalog.ExhibitionDetail.create(
				e.getId(), "무료", null, null, java.time.LocalDateTime.now()));
		if (views > 0) {
			for (int i = 0; i < views; i++) {
				e.increaseView();
			}
			exhibitionRepository.save(e);
		}
		return e.getId();
	}

	// ---------- 6.1 / 6.2 토글(멱등) ----------

	@Test
	@DisplayName("POST/DELETE bookmark — 등록 true·재등록 true(멱등)·해제 false·재해제 false(멱등)")
	void 북마크_토글_멱등() throws Exception {
		String token = loginAndGetAccessToken(8100001L, "토글유저");
		Long id = saveCatalog("BM-TOGGLE", "토글 표본전", LocalDate.now().minusDays(10), LocalDate.now().plusDays(30), 0);
		String path = "/api/v1/exhibitions/" + id + "/bookmark";

		mockMvc.perform(post(path).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.exhibitionId").value(id))
				.andExpect(jsonPath("$.data.bookmarked").value(true));
		// 멱등: 재등록해도 true
		mockMvc.perform(post(path).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookmarked").value(true));
		// 해제 → false
		mockMvc.perform(delete(path).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookmarked").value(false));
		// 멱등: 재해제해도 false
		mockMvc.perform(delete(path).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookmarked").value(false));
	}

	@Test
	@DisplayName("POST bookmark — 없는 전시, 404 NOT_FOUND")
	void 북마크_없는전시_404() throws Exception {
		String token = loginAndGetAccessToken(8100002L, "없는전시유저");
		mockMvc.perform(post("/api/v1/exhibitions/{id}/bookmark", 99999999L)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.meta.errorCode").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("POST bookmark — 미인증, 401")
	void 북마크_미인증_401() throws Exception {
		Long id = saveCatalog("BM-NOAUTH", "미인증 표본전", LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), 0);
		mockMvc.perform(post("/api/v1/exhibitions/{id}/bookmark", id))
				.andExpect(status().isUnauthorized());
	}

	// ---------- 6.3 목록 ----------

	@Test
	@DisplayName("GET /users/me/bookmarks — 관심 전시만(bookmarked=true, 항목 필드) 반환")
	void 북마크목록_항목필드() throws Exception {
		String token = loginAndGetAccessToken(8200001L, "목록유저");
		Long id = saveCatalog("BM-LIST", "관심목록 표본전", LocalDate.now().minusDays(10), LocalDate.now().plusDays(20), 0);
		mockMvc.perform(post("/api/v1/exhibitions/{id}/bookmark", id)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/users/me/bookmarks").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(1))
				.andExpect(jsonPath("$.data.content", hasSize(1)))
				.andExpect(jsonPath("$.data.content[0].exhibitionId").value(id))
				.andExpect(jsonPath("$.data.content[0].title").value("관심목록 표본전"))
				.andExpect(jsonPath("$.data.content[0].type").value("CATALOG"))
				.andExpect(jsonPath("$.data.content[0].region").value("SEOUL"))
				.andExpect(jsonPath("$.data.content[0].free").value(true))
				.andExpect(jsonPath("$.data.content[0].bookmarked").value(true));
	}

	@Test
	@DisplayName("GET /users/me/bookmarks?sort=ending — 종료 임박순(종료일 빠른 전시가 먼저)")
	void 북마크목록_ending정렬() throws Exception {
		String token = loginAndGetAccessToken(8200002L, "종료정렬유저");
		Long soon = saveCatalog("BM-END-SOON", "곧 종료전", LocalDate.now().minusDays(10), LocalDate.now().plusDays(3), 0);
		Long later = saveCatalog("BM-END-LATE", "여유 종료전", LocalDate.now().minusDays(10), LocalDate.now().plusDays(40),
				0);
		// 등록은 later 먼저 → latest 정렬이면 later가 앞이지만, ending 정렬이면 soon이 앞이어야 한다.
		mockMvc.perform(post("/api/v1/exhibitions/{id}/bookmark", later).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/exhibitions/{id}/bookmark", soon).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/users/me/bookmarks").param("sort", "ending")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].exhibitionId").value(soon))
				.andExpect(jsonPath("$.data.content[1].exhibitionId").value(later));
	}

	@Test
	@DisplayName("GET /users/me/bookmarks — 커서 페이징(size=1로 2페이지, 중복·누락 없음)")
	void 북마크목록_커서페이징() throws Exception {
		String token = loginAndGetAccessToken(8200003L, "페이징유저");
		Long a = saveCatalog("BM-PG-A", "페이징 A", LocalDate.now().minusDays(10), LocalDate.now().plusDays(30), 0);
		Long b = saveCatalog("BM-PG-B", "페이징 B", LocalDate.now().minusDays(10), LocalDate.now().plusDays(30), 0);
		mockMvc.perform(post("/api/v1/exhibitions/{id}/bookmark", a).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/exhibitions/{id}/bookmark", b).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		MvcResult page1 = mockMvc.perform(get("/api/v1/users/me/bookmarks")
						.param("sort", "latest").param("size", "1")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content", hasSize(1)))
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andExpect(jsonPath("$.data.totalCount").value(2))
				.andReturn();
		List<Integer> ids1 = JsonPath.read(page1.getResponse().getContentAsString(), "$.data.content[*].exhibitionId");
		String nextCursor = JsonPath.read(page1.getResponse().getContentAsString(), "$.data.nextCursor");

		MvcResult page2 = mockMvc.perform(get("/api/v1/users/me/bookmarks")
						.param("sort", "latest").param("size", "1").param("cursor", nextCursor)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content", hasSize(1)))
				.andReturn();
		List<Integer> ids2 = JsonPath.read(page2.getResponse().getContentAsString(), "$.data.content[*].exhibitionId");

		org.assertj.core.api.Assertions.assertThat(ids1).doesNotContainAnyElementsOf(ids2);
		org.assertj.core.api.Assertions.assertThat(ids1.size() + ids2.size()).isEqualTo(2);
	}

	@Test
	@DisplayName("GET /users/me/bookmarks?cursor=손상 — 400 INVALID_CURSOR")
	void 북마크목록_손상커서_400() throws Exception {
		String token = loginAndGetAccessToken(8200004L, "손상커서유저");
		mockMvc.perform(get("/api/v1/users/me/bookmarks").param("sort", "latest").param("cursor", "!!!corrupt!!!")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_CURSOR"));
	}

	@Test
	@DisplayName("GET /users/me — 북마크 후 stats.bookmarkCount 증가(sanity)")
	void 북마크수_증가() throws Exception {
		String token = loginAndGetAccessToken(8200005L, "카운트유저");
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.data.stats.bookmarkCount").value(0));
		Long id = saveCatalog("BM-COUNT", "카운트 표본전", LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), 0);
		mockMvc.perform(post("/api/v1/exhibitions/{id}/bookmark", id).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.data.stats.bookmarkCount").value(1));
	}

	// ---------- 5.1 배너 ----------

	@Test
	@DisplayName("GET /exhibitions/banners — 비인증 200 + {banners:[...]} shape + 최대 3개 캡")
	void 배너_최대3개_shape() throws Exception {
		LocalDate today = LocalDate.now();
		// 기존 표본보다 확실히 높은 조회수 4개 → 상위 3개만 배너에 노출되어야 한다.
		Long b1 = saveCatalog("BAN-1", "배너표본1", today.minusDays(5), today.plusDays(30), 5000);
		Long b2 = saveCatalog("BAN-2", "배너표본2", today.minusDays(5), today.plusDays(30), 4900);
		Long b3 = saveCatalog("BAN-3", "배너표본3", today.minusDays(5), today.plusDays(30), 4800);
		Long b4 = saveCatalog("BAN-4", "배너표본4", today.minusDays(5), today.plusDays(30), 4700);

		mockMvc.perform(get("/api/v1/exhibitions/banners"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meta.result").value("SUCCESS"))
				.andExpect(jsonPath("$.data.banners").isArray())
				.andExpect(jsonPath("$.data.banners", hasSize(lessThanOrEqualTo(3))))
				// 최상위 3개는 내 표본(b1,b2,b3), 4위(b4)는 캡으로 제외
				.andExpect(jsonPath("$.data.banners[*].exhibitionId", hasItem(b1.intValue())))
				.andExpect(jsonPath("$.data.banners[*].exhibitionId", hasItem(b2.intValue())))
				.andExpect(jsonPath("$.data.banners[*].exhibitionId", hasItem(b3.intValue())))
				.andExpect(jsonPath("$.data.banners[*].exhibitionId", not(hasItem(b4.intValue()))))
				.andExpect(jsonPath("$.data.banners[0].bannerImageUrl").value("https://poster/BAN-1.jpg"))
				.andExpect(jsonPath("$.data.banners[0].title").value("배너표본1"))
				.andExpect(jsonPath("$.data.banners[0].place").value("표본 장소"));
	}

	// ---------- 5.4 venueId + genre ----------

	@Test
	@DisplayName("POST /exhibitions/custom (venueId) — 성공 + 상세 place=전시관명·keywords 비어있지 않음(장르)")
	void 직접추가_venueId_장르() throws Exception {
		String token = loginAndGetAccessToken(8300001L, "전시관유저");
		Venue venue = venueRepository.searchByName("아리랑", 1).get(0);

		MvcResult created = mockMvc.perform(post("/api/v1/exhibitions/custom")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"전시관 연동전\",\"venueId\":" + venue.getId() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.type").value("CUSTOM"))
				.andReturn();
		long customId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.exhibitionId"))
				.longValue();

		mockMvc.perform(get("/api/v1/exhibitions/{id}", customId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.place").value(venue.getName()))
				.andExpect(jsonPath("$.data.region").value("SEOUL"))
				.andExpect(jsonPath("$.data.keywords").isArray())
				.andExpect(jsonPath("$.data.keywords", hasSize(1)));
	}

	@Test
	@DisplayName("POST /exhibitions/custom — 없는 venueId, 404 VENUE_NOT_FOUND")
	void 직접추가_없는venue_404() throws Exception {
		String token = loginAndGetAccessToken(8300002L, "없는전시관유저");
		mockMvc.perform(post("/api/v1/exhibitions/custom")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"없는 전시관전\",\"venueId\":88888888}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.meta.errorCode").value("VENUE_NOT_FOUND"));
	}

	@Test
	@DisplayName("POST /exhibitions/custom — format=SOLO인데 작가 없음, 400 INVALID_INPUT")
	void 직접추가_개인전_작가없음_400() throws Exception {
		String token = loginAndGetAccessToken(8300003L, "개인전유저");
		mockMvc.perform(post("/api/v1/exhibitions/custom")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"작가없는 개인전\",\"format\":\"SOLO\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
	}
}
