package modi.backend.application.exhibition;

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

import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.support.error.CoreException;

/**
 * ExhibitionSyncScheduler 단위 검증. 매일 자정 트리거 시 동기화(신규 적재) → 장르 분류(신규분) → 상세 보강을
 * 순서대로 호출하고, 실패(외부 API 불가 등)해도 예외를 삼켜 스케줄러 스레드가 죽지 않아야 한다(다음 주기 재시도).
 * 장르는 별도 주기 없이 동기화 직후에만 생성된다(신규 전시 = 미분류 행만 대상이라 멱등).
 */
class ExhibitionSyncSchedulerTest {

	private ExhibitionFacade exhibitionFacade;
	private CatalogEnricher catalogEnricher;
	private ExhibitionSyncScheduler scheduler;

	@BeforeEach
	void setUp() {
		exhibitionFacade = mock(ExhibitionFacade.class);
		catalogEnricher = mock(CatalogEnricher.class);
		scheduler = new ExhibitionSyncScheduler(exhibitionFacade, catalogEnricher);
	}

	@Test
	@DisplayName("syncDaily: 동기화 → 장르 분류(신규분) → 상세 보강을 순서대로 호출한다")
	void syncDaily_동기화후_장르_상세_순서호출() {
		given(exhibitionFacade.syncCatalog()).willReturn(3);

		scheduler.syncDaily();

		InOrder order = inOrder(exhibitionFacade, catalogEnricher);
		order.verify(exhibitionFacade, times(1)).syncCatalog();
		order.verify(catalogEnricher, times(1)).enrichGenres();
		order.verify(catalogEnricher, times(1)).enrichDetails();
	}

	@Test
	@DisplayName("syncDaily: facade가 예외를 던져도 삼켜서 다음 주기까지 살아있는다")
	void syncDaily_예외삼킴() {
		given(exhibitionFacade.syncCatalog())
				.willThrow(new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 전시 API 호출 실패"));

		assertThatCode(() -> scheduler.syncDaily()).doesNotThrowAnyException();

		verify(exhibitionFacade, times(1)).syncCatalog();
	}
}
