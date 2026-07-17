package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.domain.exhibition.PlaceHours;
import modi.backend.domain.exhibition.PlaceHoursData;
import modi.backend.domain.exhibition.PlaceHoursProvider;
import modi.backend.domain.exhibition.PlaceHoursRepository;
import modi.backend.domain.exhibition.PlaceHoursStatus;
import modi.backend.domain.exhibition.PlaceHoursVendor;
import modi.backend.domain.exhibition.WeeklyOpeningHours;
import modi.backend.infra.exhibition.GooglePlaceResponseJpaRepository;

/**
 * 전시 영업시간 보강 전 경로 통합 검증(@SpringBootTest + Testcontainers-MySQL). 외부 조회기({@link PlaceHoursProvider})만 목으로 두고
 * enricher → 벤더 원본 적재 → 우리 표시 규칙 포맷 → 정준층·전시 저장 을 실제 컴포넌트·DB로 태운다. 포맷 규칙 엣지는 목이 반환하는 주간 패턴을 바꿔가며 커버한다.
 * <p>
 * ⚠️ 전역 count()로 단언하지 않는다 — 이관 4단계에서 "매 실행 스테이징 초기화"가 사라져(벤더층이 UK upsert라 불필요)
 * 원본 행이 테스트 간에 누적된다. 검증하려던 동작("이 장소의 원본 1행")은 place_key로 조회해 그대로 지킨다.
 * {@link ExhibitionCatalogClient}도 목으로 두어 부팅 동기화가 외부 공공데이터를 건드리지 않게 한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.exhibition.enrich.scheduling-enabled=false")
class PlaceHoursIntegrationTest {

	private static final AtomicInteger SEQ = new AtomicInteger(1);

	@Autowired
	PlaceHoursEnricher placeHoursEnricher;

	@Autowired
	ExhibitionRepository exhibitionRepository;

	@Autowired
	GooglePlaceResponseJpaRepository googlePlaceResponseJpaRepository;

	@Autowired
	PlaceHoursRepository placeHoursRepository;

	@MockitoBean
	PlaceHoursProvider placeHoursProvider;

	@MockitoBean
	ExhibitionCatalogClient exhibitionCatalogClient;

	@org.junit.jupiter.api.BeforeEach
	void stubVendor() {
		// 포트 계약상 vendor()는 항상 값을 준다(정준층 provider가 NOT NULL이라 구현체는 반드시 자기 벤더를 밝힌다).
		given(placeHoursProvider.vendor()).willReturn(PlaceHoursVendor.GOOGLE);
	}

	@Test
	@DisplayName("같은 장소 전시들을 1콜로 조회해 표시값 저장 + 원본 스테이징 적재 + 매일 축약")
	void 매일축약_그리고_장소당_1콜() {
		String addr = uniqueAddr();
		Exhibition a = seedCatalog("부산현대미술관", addr);
		Exhibition b = seedCatalog("부산현대미술관", addr);
		given(placeHoursProvider.fetch(eq("부산현대미술관"), eq(addr)))
				.willReturn(Optional.of(data(addr, everyDaySameTimeButMondayClosed())));

		placeHoursEnricher.enrichPlaceHours();

		// 화~일 동일 시간 → 매일 축약, 월 휴무는 맨 아래
		assertThat(reload(a).getOperatingHours()).isEqualTo("매일 10:00 ~ 18:00\n월 휴무");
		assertThat(reload(b).getOperatingHours()).isEqualTo("매일 10:00 ~ 18:00\n월 휴무");
		assertThat(reload(a).getOperatingHoursSyncedAt()).isNotNull();
		verify(placeHoursProvider, times(1)).fetch(eq("부산현대미술관"), eq(addr)); // 장소당 1콜(중복 제거)
		assertThat(googlePlaceResponseJpaRepository.findByPlaceKey(addr)).isPresent(); // 벤더 원본 1행
		// 정준층 — 이번 단계에서 읽기 출처가 된 곳. 장소당 1행이라 전시 2건이 공유한다.
		PlaceHours canonical = placeHoursRepository.findByPlaceKey(addr).orElseThrow();
		assertThat(canonical.getFormatted()).isEqualTo("매일 10:00 ~ 18:00\n월 휴무");
		assertThat(canonical.getStatus()).isEqualTo(PlaceHoursStatus.SUCCEEDED);
		assertThat(canonical.getProvider()).isEqualTo(PlaceHoursVendor.GOOGLE);
		assertThat(canonical.getNextAttemptAt()).isNull(); // 백오프 정책은 선별을 맡는 단계의 몫 — 지어내지 않는다
	}

	@Test
	@DisplayName("시간대별 그룹 + 비연속 묶기 + 휴무 맨 아래")
	void 다중그룹_비연속() {
		String addr = uniqueAddr();
		Exhibition e = seedCatalog("갤러리", addr);
		WeeklyOpeningHours hours = WeeklyOpeningHours.builder()
				.add(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(18, 0))
				.add(DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), LocalTime.of(18, 0)) // 월·수 동일(화는 휴무 → 비연속)
				.add(DayOfWeek.THURSDAY, LocalTime.of(13, 0), LocalTime.of(20, 0))
				.add(DayOfWeek.FRIDAY, LocalTime.of(13, 0), LocalTime.of(20, 0))
				.build();
		given(placeHoursProvider.fetch(eq("갤러리"), eq(addr))).willReturn(Optional.of(data(addr, hours)));

		placeHoursEnricher.enrichPlaceHours();

		assertThat(reload(e).getOperatingHours())
				.isEqualTo("월 / 수 10:00 ~ 18:00\n목 / 금 13:00 ~ 20:00\n화 / 토 / 일 휴무");
	}

	@Test
	@DisplayName("전 요일 영업 동일 시간 → 매일, 휴무 줄 없음")
	void 전영업() {
		String addr = uniqueAddr();
		Exhibition e = seedCatalog("상시관", addr);
		WeeklyOpeningHours.Builder builder = WeeklyOpeningHours.builder();
		for (DayOfWeek d : DayOfWeek.values()) {
			builder.add(d, LocalTime.of(9, 0), LocalTime.of(21, 0));
		}
		given(placeHoursProvider.fetch(eq("상시관"), eq(addr))).willReturn(Optional.of(data(addr, builder.build())));

		placeHoursEnricher.enrichPlaceHours();

		assertThat(reload(e).getOperatingHours()).isEqualTo("매일 09:00 ~ 21:00");
	}

	@Test
	@DisplayName("장소는 찾았으나 영업시간 없음 → operating_hours null, 조회 시각은 기록, 원본은 적재")
	void 정보없음() {
		String addr = uniqueAddr();
		Exhibition e = seedCatalog("정보없는관", addr);
		given(placeHoursProvider.fetch(eq("정보없는관"), eq(addr)))
				.willReturn(Optional.of(data(addr, WeeklyOpeningHours.empty())));

		placeHoursEnricher.enrichPlaceHours();

		Exhibition reloaded = reload(e);
		assertThat(reloaded.getOperatingHours()).isNull();
		assertThat(reloaded.getOperatingHoursSyncedAt()).isNotNull(); // 백오프 — 매일 재조회 방지
		assertThat(googlePlaceResponseJpaRepository.findByPlaceKey(addr)).isPresent();
		// 장소는 찾았고 영업시간만 없다 → NO_HOURS. 현행 스키마(operating_hours=null)가 미발견과 구분 못 하던 상태다.
		assertThat(placeHoursRepository.findByPlaceKey(addr).orElseThrow().getStatus())
				.isEqualTo(PlaceHoursStatus.NO_HOURS);
	}

	@Test
	@DisplayName("장소 미발견 → operating_hours null, 조회 시각 기록, 원본 미적재")
	void 미발견() {
		String addr = uniqueAddr();
		Exhibition e = seedCatalog("없는관", addr);
		given(placeHoursProvider.fetch(eq("없는관"), eq(addr))).willReturn(Optional.empty());

		placeHoursEnricher.enrichPlaceHours();

		Exhibition reloaded = reload(e);
		assertThat(reloaded.getOperatingHours()).isNull();
		assertThat(reloaded.getOperatingHoursSyncedAt()).isNotNull();
		assertThat(googlePlaceResponseJpaRepository.findByPlaceKey(addr)).isEmpty(); // 못 찾았으니 남길 원본이 없다
		// 미발견은 NO_HOURS와 다른 사실이다 — 이 구분이 status의 존재 이유다.
		assertThat(placeHoursRepository.findByPlaceKey(addr).orElseThrow().getStatus())
				.isEqualTo(PlaceHoursStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("이미 최근 조회된 전시는 재호출 대상에서 제외")
	void 최신은_스킵() {
		String addr = uniqueAddr();
		Exhibition e = seedCatalog("최신관", addr);
		e.applyOperatingHours("기존값", LocalDateTime.now()); // 방금 조회한 것으로 표기
		exhibitionRepository.save(e);

		placeHoursEnricher.enrichPlaceHours();

		assertThat(reload(e).getOperatingHours()).isEqualTo("기존값"); // 변화 없음
		verify(placeHoursProvider, never()).fetch(eq("최신관"), eq(addr));
	}

	// ── helpers ──────────────────────────────────────────

	private String uniqueAddr() {
		return "서울 테스트구 테스트로 " + SEQ.getAndIncrement();
	}

	private Exhibition seedCatalog(String place, String placeAddr) {
		Exhibition e = Exhibition.createCatalog("ext-" + SEQ.getAndIncrement(), "제목", place, null, null,
				ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null, null, null, null, "서비스",
				null, null, null, null, null);
		// placeAddr는 상세 수집 필드 — applyDetail로 채운다(조회 대상 조건 = placeAddr not null).
		e.applyDetail(new CatalogDetailData(null, null, null, null, null, null, placeAddr, null, null));
		return exhibitionRepository.save(e);
	}

	private WeeklyOpeningHours everyDaySameTimeButMondayClosed() {
		WeeklyOpeningHours.Builder builder = WeeklyOpeningHours.builder();
		for (DayOfWeek d : new DayOfWeek[] { DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
				DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY }) {
			builder.add(d, LocalTime.of(10, 0), LocalTime.of(18, 0));
		}
		return builder.build();
	}

	private PlaceHoursData data(String addr, WeeklyOpeningHours hours) {
		// place_id·displayName·formattedAddress는 rawJson(구글 Place 응답 전체) 안에 있다 — 별도 필드로 올리지 않는다.
		return new PlaceHoursData(hours, "{\"id\":\"pid-" + addr + "\",\"formattedAddress\":\"" + addr + "\"}");
	}

	private Exhibition reload(Exhibition e) {
		return exhibitionRepository.findById(e.getId()).orElseThrow();
	}
}
