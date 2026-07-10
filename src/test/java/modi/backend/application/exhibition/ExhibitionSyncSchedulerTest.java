package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.support.error.CoreException;

/**
 * ExhibitionSyncScheduler 단위 검증. 매일 자정 트리거 시 facade.syncCatalog()·상세 보강을 호출하고,
 * 장르 백필은 3시간 주기의 별도 메서드로 분리되어 있으며, 실패(외부 API 불가 등)해도 예외를 삼켜
 * 스케줄러 스레드가 죽지 않아야 한다(다음 주기 재시도).
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
	@DisplayName("syncDaily: 동기화 후 상세 보강을 이어서 호출한다(장르 보강은 별도 주기)")
	void syncDaily_facade호출() {
		given(exhibitionFacade.syncCatalog()).willReturn(3);

		scheduler.syncDaily();

		verify(exhibitionFacade, times(1)).syncCatalog();
		verify(catalogEnricher, times(1)).enrichDetails();
		verify(catalogEnricher, never()).enrichGenres();
	}

	@Test
	@DisplayName("syncDaily: facade가 예외를 던져도 삼켜서 다음 주기까지 살아있는다")
	void syncDaily_예외삼킴() {
		given(exhibitionFacade.syncCatalog())
				.willThrow(new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 전시 API 호출 실패"));

		assertThatCode(() -> scheduler.syncDaily()).doesNotThrowAnyException();

		verify(exhibitionFacade, times(1)).syncCatalog();
	}

	@Test
	@DisplayName("enrichGenresPeriodically: 장르 백필만 호출한다(동기화·상세는 건드리지 않음)")
	void enrichGenresPeriodically_장르만() {
		scheduler.enrichGenresPeriodically();

		verify(catalogEnricher, times(1)).enrichGenres();
		verify(catalogEnricher, never()).enrichDetails();
		verify(exhibitionFacade, never()).syncCatalog();
	}

	@Test
	@DisplayName("enrichGenresPeriodically: 장르 보강 중 예외가 나도 삼켜서 다음 주기까지 살아있는다")
	void enrichGenresPeriodically_예외삼킴() {
		given(catalogEnricher.enrichGenres())
				.willThrow(new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "장르 분류 실패"));

		assertThatCode(() -> scheduler.enrichGenresPeriodically()).doesNotThrowAnyException();

		verify(catalogEnricher, times(1)).enrichGenres();
	}
}
