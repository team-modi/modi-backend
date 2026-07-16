package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.CatalogExhibitionData;
import modi.backend.domain.exhibition.CatalogListData;
import modi.backend.domain.exhibition.CultureDetailResponse;
import modi.backend.domain.exhibition.CultureDetailResponseRepository;
import modi.backend.domain.exhibition.CultureDetailStatus;
import modi.backend.domain.exhibition.CultureListResponse;
import modi.backend.domain.exhibition.CultureListResponseRepository;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.support.error.CoreException;

/**
 * 벤더층 <b>원본 적재 병행</b>(이관 3단계) 통합 검증(@SpringBootTest + Testcontainers-MySQL).
 * <p>
 * 이 테스트가 필요한 이유는 2단계-a(쓰기 이중화)와 같다: 이 단계는 <b>읽기를 바꾸지 않으므로</b> 설계상 기존 테스트
 * 전부에 보이지 않는다 — 원본 적재가 통째로 no-op가 돼도 응답도 exhibitions도 그대로라 다른 테스트는 전부 green이다.
 * 즉 "새 테이블에 실제로 쓰였는가"를 보는 테스트가 없으면 이 단계는 검증되지 않은 채로 남는다.
 * <p>
 * 함께 지키는 것: 원본 적재가 <b>기존 동기화 동작을 바꾸지 않아야</b> 한다(적재 건수·상세 완성·기간 스킵·실패 연기).
 * 그래서 매 경로에서 벤더층과 도메인층을 함께 단언한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.exhibition.enrich.scheduling-enabled=false")
class CultureVendorArchiveTest {

	private static final AtomicInteger SEQ = new AtomicInteger(1);

	@Autowired
	ExhibitionFacade exhibitionFacade;

	@Autowired
	ExhibitionRepository exhibitionRepository;

	@Autowired
	CultureListResponseRepository cultureListResponseRepository;

	@Autowired
	CultureDetailResponseRepository cultureDetailResponseRepository;

	@MockitoBean
	ExhibitionCatalogClient exhibitionCatalogClient;

	@Test
	@DisplayName("동기화 — 목록 원본이 payload·해시와 함께 벤더층에 적재된다(도메인 적재와 병행)")
	void syncCatalog_목록원본_적재() {
		String externalId = nextId();
		String payload = listPayload(externalId, "원본 적재 전시");
		given(exhibitionCatalogClient.fetchAll()).willReturn(listData(List.of(listData(externalId, "원본 적재 전시", payload))));
		given(exhibitionCatalogClient.fetchDetail(externalId)).willReturn(Optional.empty());

		int inserted = exhibitionFacade.syncCatalog();

		// 도메인층 — 기존 동작 그대로여야 한다.
		assertThat(inserted).isEqualTo(1);
		assertThat(exhibitionRepository.findByExternalId(externalId)).isPresent();
		// 벤더층 — 이번 단계에서 새로 쓰기 시작한 곳. payload는 응답 원본(매핑 JSON)이 그대로 실려야 한다.
		CultureListResponse row = listRow(externalId);
		assertThat(row.getPayload()).isEqualTo(payload);
		assertThat(row.getPayloadHash()).hasSize(64);
		assertThat(row.getFirstSeenAt()).isNotNull();
		assertThat(row.getLastSeenAt()).isEqualTo(row.getFirstSeenAt());
	}

	@Test
	@DisplayName("재동기화(값 동일) — 행을 늘리지 않고 last_seen_at만 갱신한다(UK 멱등, first_seen_at 보존)")
	void syncCatalog_재동기화_멱등() {
		String externalId = nextId();
		String payload = listPayload(externalId, "멱등 전시");
		given(exhibitionCatalogClient.fetchAll()).willReturn(listData(List.of(listData(externalId, "멱등 전시", payload))));
		given(exhibitionCatalogClient.fetchDetail(externalId)).willReturn(Optional.empty());
		exhibitionFacade.syncCatalog();
		CultureListResponse first = listRow(externalId);
		java.time.LocalDateTime firstSeen = first.getFirstSeenAt();
		String firstHash = first.getPayloadHash();

		exhibitionFacade.syncCatalog();

		CultureListResponse row = listRow(externalId); // 조회 자체가 UK 1행을 전제한다(2행이면 여기서 깨진다)
		assertThat(row.getId()).isEqualTo(first.getId()); // 새 행이 아니라 같은 행
		assertThat(row.getFirstSeenAt()).isEqualTo(firstSeen); // 처음 본 시각은 불변
		assertThat(row.getPayloadHash()).isEqualTo(firstHash); // 값이 같으니 해시도 그대로
		assertThat(row.getLastSeenAt()).isAfterOrEqualTo(firstSeen); // "이번에도 있었다"는 갱신된다
	}

	@Test
	@DisplayName("원천이 값을 정정하면 payload와 해시가 갱신된다(행 단위 변경 감지)")
	void syncCatalog_원천정정_payload갱신() {
		String externalId = nextId();
		String before = listPayload(externalId, "정정 전 제목");
		given(exhibitionCatalogClient.fetchAll()).willReturn(listData(List.of(listData(externalId, "정정 전 제목", before))));
		given(exhibitionCatalogClient.fetchDetail(externalId)).willReturn(Optional.empty());
		exhibitionFacade.syncCatalog();
		String hashBefore = listRow(externalId).getPayloadHash();

		// 원천이 같은 external_id의 내용을 고쳐 보냈다.
		String after = listPayload(externalId, "정정 후 제목");
		given(exhibitionCatalogClient.fetchAll()).willReturn(listData(List.of(listData(externalId, "정정 후 제목", after))));

		exhibitionFacade.syncCatalog();

		CultureListResponse row = listRow(externalId);
		assertThat(row.getPayload()).isEqualTo(after);
		assertThat(row.getPayloadHash()).isNotEqualTo(hashBefore);
	}

	@Test
	@DisplayName("기간이 불량해 도메인 적재에서 스킵되는 항목도 원본은 남긴다(원본은 도메인 유효성과 무관하다)")
	void syncCatalog_기간불량도_원본은_적재() {
		String externalId = nextId();
		String payload = listPayload(externalId, "역전 기간 전시");
		LocalDate today = LocalDate.now();
		// 종료일 < 시작일 — 도메인 불변식 위반이라 exhibitions엔 적재되지 않는다.
		given(exhibitionCatalogClient.fetchAll()).willReturn(listData(List.of(new CatalogExhibitionData(externalId,
				"역전 기간 전시", "장소", today, today.minusDays(1), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING,
				null, null, "기관", null, null, null, "전시", "서울", payload))));

		int inserted = exhibitionFacade.syncCatalog();

		assertThat(inserted).isZero();
		assertThat(exhibitionRepository.findByExternalId(externalId)).isEmpty(); // 도메인은 스킵
		assertThat(listRow(externalId).getPayload()).isEqualTo(payload); // 원천이 뭐라고 했는지는 남는다
	}

	@Test
	@DisplayName("상세 있음 — 상태 SUCCEEDED로 원본과 함께 기록된다")
	void syncCatalog_상세있음_SUCCEEDED() {
		String externalId = nextId();
		String detailPayload = detailPayload(externalId, "무료");
		given(exhibitionCatalogClient.fetchAll())
				.willReturn(listData(List.of(listData(externalId, "상세 있는 전시", listPayload(externalId, "상세 있는 전시")))));
		given(exhibitionCatalogClient.fetchDetail(externalId)).willReturn(Optional.of(detailData(detailPayload)));

		exhibitionFacade.syncCatalog();

		CultureDetailResponse row = detailRow(externalId);
		assertThat(row.getStatus()).isEqualTo(CultureDetailStatus.SUCCEEDED);
		assertThat(row.getPayload()).isEqualTo(detailPayload);
		assertThat(row.getAttemptCount()).isEqualTo(1);
		// 백오프 정책은 이 테이블이 대상 선별을 맡는 단계의 몫 — 지금 값을 지어내지 않는다.
		assertThat(row.getNextAttemptAt()).isNull();
	}

	@Test
	@DisplayName("원천에 상세 없음 — NO_DATA로 기록된다(현행 detail_synced_at만으론 SUCCEEDED와 구분 불가하던 상태)")
	void syncCatalog_상세없음_NO_DATA() {
		String externalId = nextId();
		given(exhibitionCatalogClient.fetchAll())
				.willReturn(listData(List.of(listData(externalId, "상세 없는 전시", listPayload(externalId, "상세 없는 전시")))));
		given(exhibitionCatalogClient.fetchDetail(externalId)).willReturn(Optional.empty());

		exhibitionFacade.syncCatalog();

		CultureDetailResponse row = detailRow(externalId);
		assertThat(row.getStatus()).isEqualTo(CultureDetailStatus.NO_DATA);
		assertThat(row.getPayload()).isNull();
		// 도메인은 기존대로 "확인 완료"만 표기해 재조회를 막는다.
		assertThat(exhibitionRepository.findByExternalId(externalId).orElseThrow().isDetailSynced()).isTrue();
	}

	@Test
	@DisplayName("상세 호출 실패 — FAILED로 기록하고, 그 행만 연기하는 기존 동작은 그대로다")
	void syncCatalog_상세실패_FAILED_기존동작보존() {
		String externalId = nextId();
		given(exhibitionCatalogClient.fetchAll())
				.willReturn(listData(List.of(listData(externalId, "상세 실패 전시", listPayload(externalId, "상세 실패 전시")))));
		given(exhibitionCatalogClient.fetchDetail(externalId))
				.willThrow(new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 전시 API 호출 실패"));

		int inserted = exhibitionFacade.syncCatalog();

		// 기존 동작: 불완전한 행을 적재하지 않고 이 행만 다음 주기로 연기한다.
		assertThat(inserted).isZero();
		assertThat(exhibitionRepository.findByExternalId(externalId)).isEmpty();
		// 벤더층엔 "시도했고 실패했다"가 남는다 — 현행 스키마가 표현하지 못하던 사실이다.
		CultureDetailResponse row = detailRow(externalId);
		assertThat(row.getStatus()).isEqualTo(CultureDetailStatus.FAILED);
		assertThat(row.getAttemptCount()).isEqualTo(1);
		// 목록 원본은 상세 실패와 무관하게 남는다(상세보다 먼저 기록하므로).
		assertThat(listRow(externalId)).isNotNull();
	}

	@Test
	@DisplayName("payload가 없으면(조각을 신뢰할 수 없는 응답) 원본을 적재하지 않는다 — 오염된 조각을 남기지 않는다")
	void syncCatalog_payload없으면_적재안함() {
		String externalId = nextId();
		given(exhibitionCatalogClient.fetchAll()).willReturn(listData(List.of(listData(externalId, "조각 없는 전시", null))));
		given(exhibitionCatalogClient.fetchDetail(externalId)).willReturn(Optional.empty());

		int inserted = exhibitionFacade.syncCatalog();

		assertThat(inserted).isEqualTo(1); // 도메인 적재는 정상 진행
		assertThat(cultureListResponseRepository.findByExternalId(externalId)).isEmpty();
	}

	/**
	 * 목록 수집 결과 래퍼 — 포트가 이제 "원천이 말한 총 건수·절단 여부"까지 돌려준다(이관 5단계, sync_run이 채울 값).
	 * 이 테스트들의 관심사가 아니라 아이템만 담고 totalCount는 수집 수와 같게 둔다(= 절단 없음).
	 */
	private static CatalogListData listData(java.util.List<CatalogExhibitionData> items) {
		return new CatalogListData(items, items.size(), false);
	}

	private String nextId() {
		return "VENDOR-" + SEQ.getAndIncrement();
	}

	/** 벤더층에 적재되는 목록 payload 모양(응답 아이템의 매핑 JSON — 도메인 변환 이전 값). */
	private String listPayload(String externalId, String title) {
		return "{\"seq\":\"" + externalId + "\",\"title\":\"" + title + "\",\"place\":\"시립미술관\","
				+ "\"realmName\":\"전시\",\"area\":\"서울\",\"serviceName\":\"전시\"}";
	}

	private String detailPayload(String externalId, String price) {
		return "{\"seq\":\"" + externalId + "\",\"price\":\"" + price + "\","
				+ "\"contents1\":\"<p>설명</p>\"}";
	}

	private CatalogExhibitionData listData(String externalId, String title, String payload) {
		LocalDate today = LocalDate.now();
		return new CatalogExhibitionData(externalId, title, "시립미술관", today.minusDays(1), today.plusDays(10),
				ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null, "기관", null, null, null, "전시",
				"서울", payload);
	}

	private CatalogDetailData detailData(String payload) {
		return new CatalogDetailData("무료", "설명", null, null, null, null, "서울시 종로구", "PLACE-1", payload);
	}

	private CultureListResponse listRow(String externalId) {
		return cultureListResponseRepository.findByExternalId(externalId).orElseThrow();
	}

	private CultureDetailResponse detailRow(String externalId) {
		return cultureDetailResponseRepository.findByExternalId(externalId).orElseThrow();
	}
}
