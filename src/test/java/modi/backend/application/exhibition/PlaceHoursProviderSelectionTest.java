package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.exhibition.sync.port.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.hours.OpeningHoursFormatter;
import modi.backend.domain.exhibition.sync.data.PlaceHoursData;
import modi.backend.domain.exhibition.sync.port.PlaceHoursProvider;
import modi.backend.infra.place.MockPlaceHoursProvider;

/**
 * 게이팅 기본값 검증 — 설정 미주입(로컬·CI·develop)에서 주 조회기가 {@link MockPlaceHoursProvider}로 선택되어 유료 실호출이 0임을 보장한다.
 * 동시에 mock 고정 샘플이 우리 표시 규칙으로 {@code 매일 10:00 ~ 18:00 / 월 휴무}가 되는지 확인한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.exhibition.enrich.scheduling-enabled=false")
class PlaceHoursProviderSelectionTest {

	@Autowired
	PlaceHoursProvider placeHoursProvider; // @Primary 로 선택된 것

	@Autowired
	OpeningHoursFormatter openingHoursFormatter;

	@MockitoBean
	ExhibitionCatalogClient exhibitionCatalogClient;

	@Test
	@DisplayName("기본 provider는 mock(유료 실호출 0) + 고정 샘플이 규칙대로 포맷된다")
	void 기본은_mock() {
		assertThat(placeHoursProvider).isInstanceOf(MockPlaceHoursProvider.class);

		// 벤더 표기는 결과가 아니라 포트가 밝힌다 — 미발견·실패 때도 "누가"를 남겨야 하기 때문이다(이관 4단계).
		assertThat(placeHoursProvider.vendor())
				.isEqualTo(modi.backend.domain.exhibition.hours.PlaceHoursVendor.MOCK);

		PlaceHoursData data = placeHoursProvider.fetch("부산현대미술관", "부산광역시 사하구 낙동남로 1191").orElseThrow();
		assertThat(openingHoursFormatter.format(data.weeklyHours())).isEqualTo("매일 10:00 ~ 18:00\n월 휴무");
	}
}
