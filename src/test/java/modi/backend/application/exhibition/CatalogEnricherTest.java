package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.config.CatalogEnrichProperties;

class CatalogEnricherTest {

	private final CatalogEnrichProperties props = new CatalogEnrichProperties(40, 20, 150);

	@Test
	@DisplayName("enrichGenres — 미분류가 소진될 때까지 배치를 반복하고, batchSize 미만 배치에서 종료한다")
	void enrichGenres_배치반복_소진시종료() {
		ExhibitionFacade facade = mock(ExhibitionFacade.class);
		// 40, 40, 15 → 세 번째가 batchSize(40) 미만이라 소진으로 보고 종료
		when(facade.initGenres(40)).thenReturn(40, 40, 15);
		CatalogEnricher enricher = new CatalogEnricher(facade, props);

		int total = enricher.enrichGenres();

		assertThat(total).isEqualTo(95);
		verify(facade, times(3)).initGenres(40);
	}

	@Test
	@DisplayName("enrichGenres — 첫 배치가 0이면 AI를 더 태우지 않고 즉시 종료한다")
	void enrichGenres_대상없으면_즉시종료() {
		ExhibitionFacade facade = mock(ExhibitionFacade.class);
		when(facade.initGenres(40)).thenReturn(0);
		CatalogEnricher enricher = new CatalogEnricher(facade, props);

		assertThat(enricher.enrichGenres()).isZero();
		verify(facade, times(1)).initGenres(40);
	}

	@Test
	@DisplayName("enrichDetails — 행마다 상세를 채우고, 한 행이 예외를 던져도 나머지는 계속한다")
	void enrichDetails_행단위_예외격리() {
		ExhibitionFacade facade = mock(ExhibitionFacade.class);
		when(facade.findCatalogIdsWithoutDetail(150)).thenReturn(List.of(1L, 2L, 3L));
		when(facade.syncCatalogDetail(1L)).thenReturn(true);
		when(facade.syncCatalogDetail(2L)).thenThrow(new RuntimeException("외부 실패"));
		when(facade.syncCatalogDetail(3L)).thenReturn(true);
		CatalogEnricher enricher = new CatalogEnricher(facade, props);

		int done = enricher.enrichDetails();

		assertThat(done).isEqualTo(2); // 1, 3 성공 / 2는 예외였지만 루프는 계속
		verify(facade).syncCatalogDetail(eq(3L));
	}

	@Test
	@DisplayName("enrichDetails — 대상이 없으면 외부 호출 없이 0을 반환한다")
	void enrichDetails_대상없음() {
		ExhibitionFacade facade = mock(ExhibitionFacade.class);
		when(facade.findCatalogIdsWithoutDetail(anyInt())).thenReturn(List.of());
		CatalogEnricher enricher = new CatalogEnricher(facade, props);

		assertThat(enricher.enrichDetails()).isZero();
	}
}
