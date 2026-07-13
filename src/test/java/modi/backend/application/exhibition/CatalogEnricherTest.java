package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.config.CatalogEnrichProperties;

/**
 * CatalogEnricher 단위 검증 — 장르 백필만 담당한다(상세는 syncCatalog가 적재 시점에 함께 채우므로 여기서 다루지 않는다).
 */
class CatalogEnricherTest {

	private final CatalogEnrichProperties props = new CatalogEnrichProperties(40, 20);

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
}
