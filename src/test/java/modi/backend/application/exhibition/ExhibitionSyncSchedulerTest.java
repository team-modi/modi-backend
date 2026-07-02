package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.support.error.CoreException;

/**
 * ExhibitionSyncScheduler 단위 검증. 매시 정각 트리거 시 facade.syncCatalog()를 호출하고,
 * 실패(외부 API 불가 등)해도 예외를 삼켜 스케줄러 스레드가 죽지 않아야 한다(다음 주기 재시도).
 */
class ExhibitionSyncSchedulerTest {

	private ExhibitionFacade exhibitionFacade;
	private ExhibitionSyncScheduler scheduler;

	@BeforeEach
	void setUp() {
		exhibitionFacade = mock(ExhibitionFacade.class);
		scheduler = new ExhibitionSyncScheduler(exhibitionFacade);
	}

	@Test
	@DisplayName("syncHourly: facade.syncCatalog()를 1회 호출한다")
	void syncHourly_facade호출() {
		given(exhibitionFacade.syncCatalog()).willReturn(3);

		scheduler.syncHourly();

		verify(exhibitionFacade, times(1)).syncCatalog();
	}

	@Test
	@DisplayName("syncHourly: facade가 예외를 던져도 삼켜서 다음 주기까지 살아있는다")
	void syncHourly_예외삼킴() {
		given(exhibitionFacade.syncCatalog())
				.willThrow(new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 전시 API 호출 실패"));

		assertThatCode(() -> scheduler.syncHourly()).doesNotThrowAnyException();

		verify(exhibitionFacade, times(1)).syncCatalog();
	}
}
