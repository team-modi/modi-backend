package modi.backend.interfaces;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
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
import modi.backend.application.exhibition.ExhibitionFacade;
import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.CatalogExhibitionData;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.infra.auth.KakaoApi;

/**
 * 전시 도메인(03_전시.md) API end-to-end 검증.
 * 외부 두 경계만 목으로 둔다: 공공데이터 수집 포트({@link ExhibitionCatalogClient})와 카카오 로그인 HTTP({@link KakaoApi}).
 * 나머지(컨트롤러·Facade·Entity·DB Testcontainers)는 실제로 태운다. CATALOG는 syncCatalog로 적재해 조회한다.
 * (프로젝트 컨벤션 우선: 성공 200 — 등록도 200. artists/keywords는 원천 미제공이라 빈 배열.)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ExhibitionIntegrationTest {

	private static final String MONET = "모네: 빛의 정원";
	private static final String PICASSO = "피카소 회고전";
	private static final String PHOTO_SHOW = "서울 골목 사진전";
	private static final String REDIRECT_URI = "http://localhost:3000/login";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ExhibitionFacade exhibitionFacade;

	@Autowired
	ExhibitionRepository exhibitionRepository;

	@MockitoBean
	ExhibitionCatalogClient catalogClient;

	@MockitoBean
	KakaoApi kakaoApi;

	/** 고정 CATALOG 표본을 목 수집 포트로 주입해 upsert 적재한다(멱등 — 재실행해도 동일 집합). */
	@BeforeEach
	void seedCatalog() {
		LocalDate today = LocalDate.now();
		given(catalogClient.fetchAll()).willReturn(List.of(
				new CatalogExhibitionData("CAT-MONET", MONET, "예술의전당", today.minusDays(10), today.plusDays(30),
						ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, "https://poster/monet.jpg",
						"https://culture.go.kr/monet", "한국문화정보원", 126.980781, 37.578608,
						"종로구", "전시", "서울"),
				new CatalogExhibitionData("CAT-PICASSO", PICASSO, "시립미술관", today.minusDays(100),
						today.minusDays(50), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null,
						"기관", null, null,
						null, "전시", "서울"),
				new CatalogExhibitionData("CAT-PHOTO", PHOTO_SHOW, "성수 갤러리", today.minusDays(5),
						today.plusDays(15), ExhibitionRegion.SEOUL, ExhibitionCategory.PHOTO, null, null,
						"기관", null, null,
						null, "사진", "서울")));
		exhibitionFacade.syncCatalog();
	}

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
	@DisplayName("GET /exhibitions — 필터 없음(비로그인), 200 + 오늘 진행 중 CATALOG만(종료 전시 제외)")
	void 목록_기본_진행중() throws Exception {
		mockMvc.perform(get("/api/v1/exhibitions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meta.result").value("SUCCESS"))
				.andExpect(jsonPath("$.data.content").isArray())
				.andExpect(jsonPath("$.data.size").value(20))
				.andExpect(jsonPath("$.data.content[*].title", hasItem(MONET)))
				.andExpect(jsonPath("$.data.content[*].title", hasItem(PHOTO_SHOW)))
				// 종료된 전시(피카소)는 진행 중이 아니므로 제외
				.andExpect(jsonPath("$.data.content[*].title", not(hasItem(PICASSO))))
				.andExpect(jsonPath("$.data.content[?(@.title=='" + MONET + "')].type").value(hasItem("CATALOG")));
	}

	@Test
	@DisplayName("GET /exhibitions?date=과거 — 그 날짜 진행 중이던 전시(피카소)만")
	void 목록_과거날짜_진행중() throws Exception {
		String pastDate = LocalDate.now().minusDays(70).toString();
		mockMvc.perform(get("/api/v1/exhibitions").param("date", pastDate))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[*].title", hasItem(PICASSO)))
				.andExpect(jsonPath("$.data.content[*].title", not(hasItem(MONET))));
	}

	@Test
	@DisplayName("GET /exhibitions?region=SEOUL&category=PHOTO — 카테고리 필터(사진전만, 회화 제외)")
	void 목록_카테고리_필터() throws Exception {
		mockMvc.perform(get("/api/v1/exhibitions").param("region", "SEOUL").param("category", "PHOTO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[*].title", hasItem(PHOTO_SHOW)))
				.andExpect(jsonPath("$.data.content[*].title", not(hasItem(MONET))));
	}

	@Test
	@DisplayName("GET /exhibitions?keyword=모네 — 전시명 부분 일치")
	void 목록_키워드_검색() throws Exception {
		mockMvc.perform(get("/api/v1/exhibitions").param("keyword", "모네"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[*].title", hasItem(MONET)))
				.andExpect(jsonPath("$.data.content[*].title", not(hasItem(PHOTO_SHOW))));
	}

	@Test
	@DisplayName("GET /exhibitions?region=SEOUL&sort=latest — 시작일 최신순(시작일이 가장 늦은 사진전이 1위)")
	void 목록_정렬_latest() throws Exception {
		// region 필터를 함께 걸어 ongoing 기본 제한을 해제 — 종료된 피카소전도 정렬 대상에 포함시켜 3건을 모두 비교한다.
		mockMvc.perform(get("/api/v1/exhibitions").param("region", "SEOUL").param("sort", "latest"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].title").value(PHOTO_SHOW));
	}

	@Test
	@DisplayName("GET /exhibitions?region=SEOUL&sort=ending — 종료일 임박순(이미 종료된 피카소전이 1위)")
	void 목록_정렬_ending() throws Exception {
		mockMvc.perform(get("/api/v1/exhibitions").param("region", "SEOUL").param("sort", "ending"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].title").value(PICASSO));
	}

	@Test
	@DisplayName("GET /exhibitions?region=SEOUL&sort=popular — 조회수순(상세를 여러 번 조회한 피카소전이 1위)")
	void 목록_정렬_popular() throws Exception {
		Long picassoId = exhibitionRepository.findByExternalId("CAT-PICASSO").orElseThrow().getId();
		// 상세 GET은 매 호출마다 ourViewCount를 1씩 증가시킨다(Exhibition.increaseView) — 3회 조회해 다른 전시보다 조회수를 확실히 앞세운다.
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(get("/api/v1/exhibitions/{id}", picassoId)).andExpect(status().isOk());
		}

		mockMvc.perform(get("/api/v1/exhibitions").param("region", "SEOUL").param("sort", "popular"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].title").value(PICASSO));
	}

	@Test
	@DisplayName("GET /exhibitions?region=NOPE — 잘못된 enum, 400 INVALID_INPUT")
	void 목록_잘못된_enum_400() throws Exception {
		mockMvc.perform(get("/api/v1/exhibitions").param("region", "NOPE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("GET /exhibitions?date=abc — 잘못된 날짜 포맷, 400 INVALID_INPUT")
	void 목록_잘못된_날짜_400() throws Exception {
		mockMvc.perform(get("/api/v1/exhibitions").param("date", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("GET /exhibitions/{id} — 존재하는 CATALOG, 비로그인 200 + 상세(artists/keywords 빈 배열)")
	void 상세_카탈로그_공개() throws Exception {
		Long id = exhibitionRepository.findByExternalId("CAT-MONET").orElseThrow().getId();
		mockMvc.perform(get("/api/v1/exhibitions/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.exhibitionId").value(id))
				.andExpect(jsonPath("$.data.type").value("CATALOG"))
				.andExpect(jsonPath("$.data.title").value(MONET))
				.andExpect(jsonPath("$.data.region").value("SEOUL"))
				.andExpect(jsonPath("$.data.category").value("PAINTING"))
				.andExpect(jsonPath("$.data.artists").isArray())
				.andExpect(jsonPath("$.data.artists").isEmpty())
				.andExpect(jsonPath("$.data.keywords").isArray())
				.andExpect(jsonPath("$.data.keywords").isEmpty());
	}

	@Test
	@DisplayName("GET /exhibitions/{id} — CATALOG 상세 진입 시 지연수집 필드(주소·이미지·문의·조회수·홈페이지) 노출, 내부 보존 필드는 비노출")
	void 상세_지연수집_필드_노출() throws Exception {
		Long id = exhibitionRepository.findByExternalId("CAT-MONET").orElseThrow().getId();
		given(catalogClient.fetchDetail("CAT-MONET")).willReturn(Optional.of(
				new CatalogDetailData("성인 20,000원", "모네 특별전 설명", "https://detail/monet", "02-1234-5678",
						"https://img/monet.jpg", "https://place/monet", "서울 어딘가", "PLACE-SEQ-1")));

		mockMvc.perform(get("/api/v1/exhibitions/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.address").value("서울 어딘가"))
				.andExpect(jsonPath("$.data.imgUrl").value("https://img/monet.jpg"))
				.andExpect(jsonPath("$.data.phone").value("02-1234-5678"))
				.andExpect(jsonPath("$.data.viewCount").value(greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.data.sigungu").value("종로구"))
				.andExpect(jsonPath("$.data.placeUrl").value("https://place/monet"))
				.andExpect(jsonPath("$.data.realmName").doesNotExist())
				.andExpect(jsonPath("$.data.areaText").doesNotExist())
				.andExpect(jsonPath("$.data.placeSeq").doesNotExist());
	}

	@Test
	@DisplayName("GET /exhibitions/{id} — 존재하지 않는 전시, 404 NOT_FOUND")
	void 상세_없음_404() throws Exception {
		mockMvc.perform(get("/api/v1/exhibitions/{id}", 99999999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.meta.errorCode").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("POST /exhibitions/custom — 로그인+제목만, 200 + exhibitionId·type=CUSTOM")
	void 개인전시_등록_정상() throws Exception {
		String token = loginAndGetAccessToken(7000001L, "등록유저");
		mockMvc.perform(post("/api/v1/exhibitions/custom")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"동네 골목 사진전\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.exhibitionId").isNumber())
				.andExpect(jsonPath("$.data.type").value("CUSTOM"));
	}

	@Test
	@DisplayName("POST /exhibitions/custom — 전시 형태·작가 포함 등록 → 상세에 format=SOLO·artists=[작가] 반영")
	void 개인전시_등록_전시형태_작가() throws Exception {
		String token = loginAndGetAccessToken(7000010L, "형태유저");
		MvcResult created = mockMvc.perform(post("/api/v1/exhibitions/custom")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "title": "조용한 오후",
								  "place": "아리랑 문화관",
								  "startDate": "2026-06-24",
								  "endDate": "2026-07-31",
								  "region": "SEOUL",
								  "format": "SOLO",
								  "artist": "김선영"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.type").value("CUSTOM"))
				.andReturn();
		long customId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.exhibitionId"))
				.longValue();

		mockMvc.perform(get("/api/v1/exhibitions/{id}", customId)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.format").value("SOLO"))
				.andExpect(jsonPath("$.data.place").value("아리랑 문화관"))
				.andExpect(jsonPath("$.data.artists", hasSize(1)))
				.andExpect(jsonPath("$.data.artists[0]").value("김선영"));
	}

	@Test
	@DisplayName("POST /exhibitions/custom — 정의되지 않은 전시 형태, 400 INVALID_INPUT")
	void 개인전시_등록_잘못된_형태_400() throws Exception {
		String token = loginAndGetAccessToken(7000011L, "형태오류유저");
		mockMvc.perform(post("/api/v1/exhibitions/custom")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"형태오류전\",\"format\":\"UNKNOWN\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("POST /exhibitions/custom — 비로그인, 401")
	void 개인전시_등록_미인증_401() throws Exception {
		mockMvc.perform(post("/api/v1/exhibitions/custom")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"무단 등록\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("POST /exhibitions/custom — 제목 누락, 400 INVALID_INPUT")
	void 개인전시_등록_제목누락_400() throws Exception {
		String token = loginAndGetAccessToken(7000002L, "제목유저");
		mockMvc.perform(post("/api/v1/exhibitions/custom")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"place\":\"성수동\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("POST /exhibitions/custom — 종료일<시작일, 400 INVALID_INPUT")
	void 개인전시_등록_기간역전_400() throws Exception {
		String token = loginAndGetAccessToken(7000003L, "기간유저");
		mockMvc.perform(post("/api/v1/exhibitions/custom")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"기간 역전전\",\"startDate\":\"2026-06-30\",\"endDate\":\"2026-06-20\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("CUSTOM 노출/접근 — 등록자 본인만 목록·상세, 타인/비로그인은 403·미노출")
	void 개인전시_노출_권한() throws Exception {
		String ownerToken = loginAndGetAccessToken(7100001L, "소유자");
		String otherToken = loginAndGetAccessToken(7100002L, "타인");
		String uniqueTitle = "소유자만 보는 개인전-7100001";

		MvcResult created = mockMvc.perform(post("/api/v1/exhibitions/custom")
						.header("Authorization", "Bearer " + ownerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"" + uniqueTitle + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		long customId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.exhibitionId"))
				.longValue();

		// 목록: 본인은 보이고, 비로그인/타인 목록엔 없음
		mockMvc.perform(get("/api/v1/exhibitions").param("keyword", uniqueTitle)
						.header("Authorization", "Bearer " + ownerToken))
				.andExpect(jsonPath("$.data.content[*].title", hasItem(uniqueTitle)));
		mockMvc.perform(get("/api/v1/exhibitions").param("keyword", uniqueTitle))
				.andExpect(jsonPath("$.data.content[*].title", not(hasItem(uniqueTitle))));

		// 상세: 본인 200, 타인 403, 비로그인 403
		mockMvc.perform(get("/api/v1/exhibitions/{id}", customId)
						.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.type").value("CUSTOM"));
		mockMvc.perform(get("/api/v1/exhibitions/{id}", customId)
						.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.meta.errorCode").value("FORBIDDEN"));
		mockMvc.perform(get("/api/v1/exhibitions/{id}", customId))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.meta.errorCode").value("FORBIDDEN"));
	}
}
