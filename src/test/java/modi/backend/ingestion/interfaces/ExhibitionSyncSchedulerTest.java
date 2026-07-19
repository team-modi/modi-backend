package modi.backend.ingestion.interfaces;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import modi.backend.domain.exhibition.catalog.ExhibitionErrorCode;
import modi.backend.support.error.CoreException;
import modi.backend.ingestion.application.enricher.CatalogEnricher;
import modi.backend.ingestion.application.CatalogSynchronizer;
import modi.backend.ingestion.application.enricher.PlaceHoursEnricher;

/**
 * ExhibitionSyncScheduler 단위 검증. 매일 자정 트리거 시 동기화(목록+상세 한 패스) → 장르 분류(신규분)를
 * 순서대로 호출하고, 실패(외부 API 불가 등)해도 예외를 삼켜 스케줄러 스레드가 죽지 않아야 한다(다음 주기 재시도).
 * 상세(가격 등)는 syncCatalog가 적재 시점에 함께 채우므로 별도 상세 보강 호출이 없다.
 * 장르는 별도 주기 없이 동기화 직후에만 생성된다(신규 전시 = 미분류 행만 대상이라 멱등).
 */
class ExhibitionSyncSchedulerTest {

	private CatalogSynchronizer catalogSynchronizer;
	private CatalogEnricher catalogEnricher;
	private PlaceHoursEnricher placeHoursEnricher;
	private ExhibitionSyncScheduler scheduler;

	@BeforeEach
	void setUp() {
		catalogSynchronizer = mock(CatalogSynchronizer.class);
		catalogEnricher = mock(CatalogEnricher.class);
		placeHoursEnricher = mock(PlaceHoursEnricher.class);
		scheduler = new ExhibitionSyncScheduler(catalogSynchronizer, catalogEnricher, placeHoursEnricher);
	}

	@Test
	@DisplayName("syncDaily: 동기화(목록+상세) → 장르 분류(신규분)를 순서대로 호출한다")
	void syncDaily_동기화후_장르_순서호출() {
		given(catalogSynchronizer.syncCatalog()).willReturn(3);

		scheduler.syncDaily();

		InOrder order = inOrder(catalogSynchronizer, catalogEnricher);
		order.verify(catalogSynchronizer, times(1)).syncCatalog();
		order.verify(catalogEnricher, times(1)).enrichGenres();
	}

	@Test
	@DisplayName("syncDaily: facade가 예외를 던져도 삼켜서 다음 주기까지 살아있는다")
	void syncDaily_예외삼킴() {
		given(catalogSynchronizer.syncCatalog())
				.willThrow(new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 전시 API 호출 실패"));

		assertThatCode(() -> scheduler.syncDaily()).doesNotThrowAnyException();

		verify(catalogSynchronizer, times(1)).syncCatalog();
	}
}
