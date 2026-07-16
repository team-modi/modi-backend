package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.domain.exhibition.PlaceHours;
import modi.backend.domain.exhibition.PlaceHoursRepository;
import modi.backend.domain.exhibition.PlaceHoursStatus;
import modi.backend.domain.exhibition.PlaceHoursVendor;

/**
 * 영업시간 <b>읽기 전환</b>(이관 4단계-b) 검증(@SpringBootTest + Testcontainers-MySQL).
 * <p>
 * <b>이 테스트가 없으면 읽기 전환은 통째로 검증되지 않는다.</b> 작업 시작 시점에 {@code DetailResponse.operatingHours}를
 * 단언하는 테스트는 <b>레포 전체에 0개</b>였다 — 즉 상세의 영업시간이 조용히 사라져도 스위트는 전부 green이다.
 * 핸드오프가 {@code PlaceHoursIntegrationTest}를 operatingHours 방어선으로 지목했지만, 그건
 * {@code exhibitions.operating_hours} <b>컬럼</b>을 볼 뿐이고 그 컬럼은 쓰기 이중화로 계속 채워지므로
 * <b>읽기 출처가 바뀐 것을 원리적으로 감지하지 못한다</b>(2단계에서 {@code BookmarkIntegrationTest}가 그랬던 것과 같다).
 * <p>
 * 그래서 여기서는 <b>두 위치를 일부러 다른 값으로 갈라놓고</b> 어느 쪽이 나오는지 본다 — 이렇게 해야 읽기를 레거시로
 * 되돌렸을 때 테스트가 실제로 FAIL한다. 읽기 전환의 위험은 터지지 않는 회귀다: operatingHours가 조용히 null이 돼도
 * 200이 나가고 로그도 없다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.exhibition.enrich.scheduling-enabled=false")
class PlaceHoursReadSwitchTest {

	private static final AtomicInteger SEQ = new AtomicInteger(1);

	@Autowired
	ExhibitionFacade exhibitionFacade;

	@Autowired
	ExhibitionRepository exhibitionRepository;

	@Autowired
	PlaceHoursRepository placeHoursRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	ExhibitionCatalogClient exhibitionCatalogClient;

	@Test
	@DisplayName("상세 operatingHours는 정준층에서 온다 — 레거시 컬럼과 값이 갈리면 정준층 값이 나온다")
	void 상세_operatingHours_정준층에서_읽는다() {
		// 두 위치를 일부러 다르게 심는다. 같게 두면 어느 쪽을 읽는지 구분할 수 없다.
		Exhibition e = seedCatalogWithAddr();
		e.applyOperatingHours("레거시 09:00 ~ 17:00", LocalDateTime.now()); // exhibitions.operating_hours
		exhibitionRepository.save(e);
		placeHoursRepository.save(PlaceHours.first(e.getPlaceKey(), "매일 10:00 ~ 18:00\n월 휴무", // 정준층 = 진실 원천
				PlaceHoursStatus.SUCCEEDED, PlaceHoursVendor.GOOGLE));

		ExhibitionResult.Detail detail = exhibitionFacade.getDetail(
				new ExhibitionCriteria.Detail(e.getId(), null));

		assertThat(detail.operatingHours()).isEqualTo("매일 10:00 ~ 18:00\n월 휴무");
	}

	@Test
	@DisplayName("정준층에 행이 없으면 operatingHours는 null — 레거시 컬럼에 값이 있어도 폴백하지 않는다")
	void 정준층_행없음_레거시로_폴백하지_않는다() {
		// 폴백("없으면 operating_hours에서 읽기")을 넣으면 진실 원천이 모호해지고, 정준층 쓰기가 통째로 빠져도
		// 아무도 눈치채지 못한다. 전 경로는 쓰기 이중화 + V23 백필이 덮으므로 폴백은 불필요하다.
		Exhibition e = seedCatalogWithAddr();
		e.applyOperatingHours("레거시만 있는 값", LocalDateTime.now());
		exhibitionRepository.save(e);

		ExhibitionResult.Detail detail = exhibitionFacade.getDetail(
				new ExhibitionCriteria.Detail(e.getId(), null));

		assertThat(detail.operatingHours()).isNull();
	}

	@Test
	@DisplayName("같은 장소의 전시들은 정준층 한 행을 공유한다(장소당 1행 — 전시 수와 무관)")
	void 같은장소_전시들이_한행을_공유한다() {
		String addr = uniqueAddr();
		Exhibition a = seedCatalogWithAddr(addr);
		Exhibition b = seedCatalogWithAddr(addr);
		placeHoursRepository.save(PlaceHours.first(a.getPlaceKey(), "매일 11:00 ~ 19:00",
				PlaceHoursStatus.SUCCEEDED, PlaceHoursVendor.GOOGLE));

		assertThat(detailOf(a).operatingHours()).isEqualTo("매일 11:00 ~ 19:00");
		assertThat(detailOf(b).operatingHours()).isEqualTo("매일 11:00 ~ 19:00");
	}

	@Test
	@DisplayName("스냅샷 조회(getForSnapshot)의 operatingHours도 같은 출처를 본다")
	void 스냅샷_operatingHours_정준층에서_읽는다() {
		Exhibition e = seedCatalogWithAddr();
		e.applyOperatingHours("레거시 값", LocalDateTime.now());
		exhibitionRepository.save(e);
		placeHoursRepository.save(PlaceHours.first(e.getPlaceKey(), "정준 값",
				PlaceHoursStatus.SUCCEEDED, PlaceHoursVendor.UNKNOWN));

		ExhibitionResult.Detail detail = exhibitionFacade.getForSnapshot(e.getId(), null);

		assertThat(detail.operatingHours()).isEqualTo("정준 값");
	}

	@Test
	@DisplayName("V23 백필 — 레거시 값만 있던 장소를 provider=UNKNOWN으로 채워 상세 영업시간을 살린다(멱등)")
	void v23백필_UNKNOWN으로_채우고_상세를_살린다() {
		// 테스트 컨테이너는 빈 DB에 마이그레이션을 적용하므로 V23이 실제로 채우는 장면이 재현되지 않는다.
		// 그래서 배포되는 파일 그 자체를 읽어 시드 데이터에 적용한다(SQL을 테스트에 복붙하면 파일을 검증하지 못한다).
		Exhibition e = seedCatalogWithAddr();
		e.applyOperatingHours("매일 10:00 ~ 18:00", LocalDateTime.now());
		exhibitionRepository.save(e);
		// 백필 전엔 정준층이 비어 있어 상세 영업시간이 사라진다 — 이게 백필이 없을 때의 회귀 그 자체다.
		assertThat(detailOf(e).operatingHours()).isNull();

		runV23();

		PlaceHours backfilled = placeHoursRepository.findByPlaceKey(e.getPlaceKey()).orElseThrow();
		assertThat(backfilled.getFormatted()).isEqualTo("매일 10:00 ~ 18:00"); // 값 보존 → 상세가 살아난다
		assertThat(backfilled.getStatus()).isEqualTo(PlaceHoursStatus.SUCCEEDED);
		assertThat(backfilled.getProvider()).isEqualTo(PlaceHoursVendor.UNKNOWN); // 출처 기록이 없다 — 지어내지 않는다
		assertThat(backfilled.getAttemptCount()).isZero(); // 우리가 센 시도가 없다
		assertThat(backfilled.getNextAttemptAt()).isNull();
		assertThat(detailOf(e).operatingHours()).isEqualTo("매일 10:00 ~ 18:00"); // 회귀 없음
	}

	@Test
	@DisplayName("V23 백필 — 영업시간이 없던 장소는 행을 만들지 않는다(NOT_FOUND·NO_HOURS를 지어내지 않는다)")
	void v23백필_값없으면_행을_만들지_않는다() {
		// 현행 스키마는 "장소 미발견"과 "영업시간 정보 없음"을 구분하지 못한다(둘 다 operating_hours=null).
		// 둘 중 하나로 찍으면 없는 사실을 지어내는 것이라, 아예 안 만든다 — 읽기 결과(null)는 어차피 지금과 같다.
		Exhibition e = seedCatalogWithAddr();
		e.applyOperatingHours(null, LocalDateTime.now()); // 조회는 했으나 값이 없던 장소
		exhibitionRepository.save(e);

		runV23();

		assertThat(placeHoursRepository.findByPlaceKey(e.getPlaceKey())).isEmpty();
	}

	@Test
	@DisplayName("V23 백필 — 이미 정준층에 있는 행은 덮지 않는다(계보 보존 + 재실행 멱등)")
	void v23백필_기존행_보존_멱등() {
		Exhibition e = seedCatalogWithAddr();
		e.applyOperatingHours("레거시 값", LocalDateTime.now());
		exhibitionRepository.save(e);
		// 쓰기 이중화가 이미 계보와 함께 채워둔 행 — 백필이 이걸 UNKNOWN으로 덮으면 계보를 잃는다.
		placeHoursRepository.save(PlaceHours.first(e.getPlaceKey(), "실호출 값",
				PlaceHoursStatus.SUCCEEDED, PlaceHoursVendor.GOOGLE));

		runV23();
		runV23(); // 재실행해도 결과가 같아야 한다

		PlaceHours kept = placeHoursRepository.findByPlaceKey(e.getPlaceKey()).orElseThrow();
		assertThat(kept.getFormatted()).isEqualTo("실호출 값");
		assertThat(kept.getProvider()).isEqualTo(PlaceHoursVendor.GOOGLE);
	}

	// ── 헬퍼 ────────────────────────────────────────────────────────────────────

	/** 배포되는 V23 파일을 그대로 읽어 실행한다. */
	private void runV23() {
		jdbcTemplate.execute(readMigration("db/migration/V23__backfill_place_hours.sql"));
	}

	private static String readMigration(String classpath) {
		try (var in = PlaceHoursReadSwitchTest.class.getClassLoader().getResourceAsStream(classpath)) {
			// 주석은 실행에 무해하므로 그대로 두고, 파일 전체를 한 문장으로 실행한다(V23은 단일 INSERT).
			return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (Exception ex) {
			throw new IllegalStateException("V23 마이그레이션 파일을 읽지 못했다: " + classpath, ex);
		}
	}

	private ExhibitionResult.Detail detailOf(Exhibition e) {
		return exhibitionFacade.getDetail(new ExhibitionCriteria.Detail(e.getId(), null));
	}

	private String uniqueAddr() {
		return "서울 읽기전환구 테스트로 " + SEQ.getAndIncrement();
	}

	private Exhibition seedCatalogWithAddr() {
		return seedCatalogWithAddr(uniqueAddr());
	}

	private Exhibition seedCatalogWithAddr(String addr) {
		Exhibition e = Exhibition.createCatalog("HOURS-READ-" + SEQ.getAndIncrement(), "영업시간 읽기 전환 전시",
				"시립미술관", null, null, ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null, null,
				null, null, "기관", null, null, null, "전시", "서울");
		// place_addr·place_key는 상세 수집에서 채워진다(place_key는 주소에서 파생 — 산출 지점이 한 곳이다).
		e.applyDetail(new CatalogDetailData(null, null, null, null, null, null, addr, null, null));
		return exhibitionRepository.save(e);
	}
}
