package modi.backend.interfaces.exhibition;

import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import modi.backend.interfaces.auth.LoginUser;
import modi.backend.interfaces.common.dto.CursorResponse;
import modi.backend.interfaces.exhibition.dto.ExhibitionDto;

/**
 * 전시 API Swagger 스펙(03_전시.md). MVC 어노테이션은 {@link ExhibitionV1Controller}.
 */
@Tag(name = "Exhibition", description = """
		전시 목록/탐색 · 상세 · 개인 전시 등록.

		## 공통 응답 포맷(ApiResponse)
		모든 응답은 아래 봉투(envelope)로 감싸진다. 성공은 예외 없이 HTTP 200이며(프로젝트 컨벤션 — 201 미사용),
		실패는 에러의 HTTP 상태 코드와 함께 meta.result=FAIL · meta.errorCode · meta.message가 내려간다.
		```
		{
		  "meta": { "result": "SUCCESS" | "FAIL", "errorCode": string | null, "message": string | null },
		  "data": <T> | null
		}
		```
		검증(Bean Validation) 실패는 위 포맷의 data에 필드별 오류 배열이 추가로 담긴다:
		```
		"data": [ { "field": "title", "value": "", "reason": "공백일 수 없습니다" } ]
		```

		## 공통 에러 코드(ErrorType)
		| errorCode | HTTP | 의미 |
		|---|---|---|
		| INVALID_INPUT | 400 | 요청 파라미터/바디가 올바르지 않음(형식 오류·미정의 enum 코드 등) |
		| INVALID_CURSOR | 400 | 커서-조건 불일치·손상된 커서 |
		| UNAUTHORIZED | 401 | 인증(Bearer access 토큰)이 필요하거나 무효함 |
		| FORBIDDEN | 403 | 인증은 됐으나 해당 리소스 접근 권한이 없음 |
		| NOT_FOUND | 404 | 요청한 리소스를 찾을 수 없음 |
		| METHOD_NOT_ALLOWED | 405 | 허용되지 않은 HTTP 메서드 |
		| INTERNAL_ERROR | 500 | 서버 내부 오류 |

		## 전시 도메인 에러 코드(ExhibitionErrorCode)
		| errorCode | HTTP | 의미 |
		|---|---|---|
		| NOT_FOUND | 404 | 요청한 전시를 찾을 수 없음 |
		| EXTERNAL_API_UNAVAILABLE | 503 | 외부 전시 정보(문화포털 등)를 불러올 수 없음 |
		""")
public interface ExhibitionV1ApiSpec {

	@Operation(summary = "전시 목록/탐색", description = """
			필터·정렬·커서로 전시를 조회한다(커서 페이지네이션). 필터 미지정 시 오늘 진행 중인 전시를 기본 노출한다.
			비로그인은 CATALOG만, 로그인은 CATALOG + 본인 CUSTOM을 함께 본다(로그인 시 bookmarked 개인화).
			인증은 선택(Optional)이다 — Authorization 헤더가 없거나 토큰이 무효해도 401을 내지 않고
			비로그인(익명)으로 취급해 조회를 계속한다.
			정렬이 바뀌면 커서를 버리고 처음부터 재조회한다(커서의 정렬 판별자와 sort가 다르면 INVALID_CURSOR).""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = CursorResponse.class),
					examples = @ExampleObject(name = "목록 조회 성공", value = """
							{
							  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
							  "data": {
							    "content": [
							      {
							        "exhibitionId": 51,
							        "type": "CATALOG",
							        "title": "모네: 빛을 그리다",
							        "posterUrl": "https://cdn.modi.app/exhibitions/51/poster.jpg",
							        "startDate": "2026-06-01",
							        "endDate": "2026-08-31",
							        "place": "예술의전당 한가람미술관",
							        "region": "SEOUL",
							        "category": "PAINTING",
							        "artistSummary": null,
							        "dDay": 5,
							        "free": false,
							        "bookmarked": false
							      }
							    ],
							    "nextCursor": "eyJzb3J0IjoibGF0ZXN0IiwibGFzdElkIjo1MX0",
							    "hasNext": true,
							    "totalCount": 160
							  }
							}
							"""))),
			@ApiResponse(responseCode = "400", description = "INVALID_INPUT — keyword 1글자, sort=distance인데 lat·lng 없음, "
					+ "미정의 region/category/section 코드, date 형식 오류 / INVALID_CURSOR — 커서-정렬 불일치·손상",
					content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					examples = @ExampleObject(name = "거리순인데 좌표 없음", value = """
							{
							  "meta": { "result": "FAIL", "errorCode": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다." },
							  "data": null
							}
							"""))),
	})
	ResponseEntity<modi.backend.support.response.ApiResponse<CursorResponse<ExhibitionDto.ListItemResponse>>> list(
			@Parameter(description = "전시명·전시장명 부분 일치(최소 2글자)", example = "모네") String keyword,
			@Parameter(description = "섹션 필터", schema = @Schema(allowableValues = { "ending-soon",
					"opening-this-month", "free" }), example = "ending-soon") String section,
			@Parameter(description = "opening-this-month 기간 범위(기본 month)",
					schema = @Schema(allowableValues = { "month", "week" }), example = "month") String period,
			@Parameter(description = "지역 코드 콤마 다중(SEOUL,GYEONGGI 등)", example = "SEOUL,GYEONGGI") String region,
			@Parameter(description = "카테고리 코드 콤마 다중(PAINTING·PHOTO·MEDIA·SCULPTURE·DESIGN·CRAFT·"
					+ "ARCHITECTURE·PERFORMANCE·ETC)", example = "PHOTO,MEDIA") String category,
			@Parameter(description = "해당 날짜에 진행 중인 전시(YYYY-MM-DD)", example = "2026-06-30") String date,
			@Parameter(description = "정렬 코드. latest=시작일 최신순(기본), ending=종료일 임박순, popular=조회수 많은순, "
					+ "distance=거리순(lat·lng 필수)", example = "latest",
					schema = @Schema(allowableValues = { "latest", "ending", "popular", "distance" })) String sort,
			@Parameter(description = "위도(sort=distance 필수)", example = "37.5033") Double lat,
			@Parameter(description = "경도(sort=distance 필수)", example = "126.9575") Double lng,
			@Parameter(description = "다음 페이지 조회용 opaque 커서(첫 페이지는 생략)") String cursor,
			@Parameter(description = "페이지 크기(기본 20, 최대 50)", example = "20") Integer size,
			@Parameter(hidden = true) Optional<LoginUser> loginUser);

	@Operation(summary = "홈 배너 조회", description = """
			홈 상단 캐러셀용 배너를 최대 3개 조회한다(03_전시.md E-10). 공개 API(인증 불필요).
			현재는 오늘 진행 중인 전시 중 조회수 상위 최대 3개를 노출한다(운영자 지정 기능은 추후).
			진행 중 전시가 없으면 data.banners는 빈 배열이다.
			홈 화면은 이 배너 1콜과 섹션 조회(GET /exhibitions?section=...) 3콜을 병렬로 호출한다.""")
	@ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(
			mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = ExhibitionDto.BannersResponse.class),
			examples = @ExampleObject(name = "배너 조회 성공", value = """
					{
					  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
					  "data": {
					    "banners": [
					      {
					        "exhibitionId": 51,
					        "title": "모네: 빛을 그리다",
					        "bannerImageUrl": "https://cdn.modi.app/exhibitions/51/poster.jpg",
					        "startDate": "2026-06-01",
					        "endDate": "2026-08-31",
					        "place": "예술의전당 한가람미술관"
					      }
					    ]
					  }
					}"""))))
	ResponseEntity<modi.backend.support.response.ApiResponse<ExhibitionDto.BannersResponse>> banners();

	@Operation(summary = "전시 상세", description = """
			CATALOG는 공개, CUSTOM은 등록자 본인만 조회 가능. 인증은 선택(Optional) —
			비로그인·무효 토큰이어도 CATALOG 전시는 정상 조회된다. 로그인 시 bookmarked·recorded 개인화 필드를 채운다.""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ExhibitionDto.DetailResponse.class),
					examples = @ExampleObject(name = "상세 조회 성공", value = """
							{
							  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
							  "data": {
							    "exhibitionId": 51,
							    "type": "CATALOG",
							    "title": "모네: 빛을 그리다",
							    "posterUrl": "https://cdn.modi.app/exhibitions/51/poster.jpg",
							    "startDate": "2026-06-01",
							    "endDate": "2026-08-31",
							    "place": "예술의전당 한가람미술관",
							    "region": "SEOUL",
							    "category": "PAINTING",
							    "description": "인상주의 거장 모네의 대표작을 만나는 특별전.",
							    "operatingHours": "매일 10:00 ~ 18:00",
							    "price": "무료",
							    "artists": ["클로드 모네"],
							    "keywords": ["인상주의", "회화"],
							    "serviceName": "문화포털",
							    "detailUrl": "https://culture.go.kr/exhibitions/51",
							    "gpsX": 127.0136,
							    "gpsY": 37.4783,
							    "address": "서울특별시 서초구 남부순환로 2406",
							    "imgUrl": "https://cdn.modi.app/exhibitions/51/detail.jpg",
							    "phone": "02-580-1300",
							    "viewCount": 1024,
							    "sigungu": "서초구",
							    "placeUrl": "https://www.sac.or.kr",
							    "artistSummary": null,
							    "free": true,
							    "bookmarked": true,
							    "recorded": false
							  }
							}
							"""))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — 타인이 등록한 CUSTOM 전시에 접근",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(name = "타인의 CUSTOM 전시 접근", value = """
									{
									  "meta": { "result": "FAIL", "errorCode": "FORBIDDEN", "message": "접근 권한이 없습니다." },
									  "data": null
									}
									"""))),
			@ApiResponse(responseCode = "404", description = "NOT_FOUND — 요청한 exhibitionId의 전시가 존재하지 않음",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(name = "존재하지 않는 전시", value = """
									{
									  "meta": { "result": "FAIL", "errorCode": "NOT_FOUND", "message": "요청한 전시를 찾을 수 없습니다." },
									  "data": null
									}
									"""))),
	})
	ResponseEntity<modi.backend.support.response.ApiResponse<ExhibitionDto.DetailResponse>> detail(
			@Parameter(description = "전시 ID", example = "51") Long exhibitionId,
			@Parameter(hidden = true) Optional<LoginUser> loginUser);

	@Operation(summary = "개인 전시 등록", description = "카탈로그에 없는 개인 전시를 직접 등록한다. access 토큰 필요(인증 필수).")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "등록 성공(프로젝트 컨벤션상 201 대신 200 사용)",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ExhibitionDto.CreatedResponse.class),
							examples = @ExampleObject(name = "등록 성공", value = """
									{
									  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
									  "data": { "exhibitionId": 108, "type": "CUSTOM" }
									}
									"""))),
			@ApiResponse(responseCode = "400", description = "INVALID_INPUT — 제목 공백, 종료일이 시작일보다 빠름, "
					+ "미정의 region/category 코드 등. Bean Validation 실패 시 필드별 오류가 data에 배열로 담긴다.",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = {
									@ExampleObject(name = "제목 공백(필드 검증 실패)", value = """
											{
											  "meta": { "result": "FAIL", "errorCode": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다." },
											  "data": [
											    { "field": "title", "value": "", "reason": "공백일 수 없습니다" }
											  ]
											}
											"""),
									@ExampleObject(name = "종료일이 시작일보다 빠름(도메인 규칙 위반)", value = """
											{
											  "meta": { "result": "FAIL", "errorCode": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다." },
											  "data": null
											}
											"""),
							})),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED — Bearer access 토큰이 없거나 무효함",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							examples = @ExampleObject(name = "토큰 없음/무효", value = """
									{
									  "meta": { "result": "FAIL", "errorCode": "UNAUTHORIZED", "message": "인증이 필요합니다." },
									  "data": null
									}
									"""))),
	})
	ResponseEntity<modi.backend.support.response.ApiResponse<ExhibitionDto.CreatedResponse>> registerCustom(
			@Parameter(hidden = true) LoginUser user,
			ExhibitionDto.CustomCreateRequest request);

}
