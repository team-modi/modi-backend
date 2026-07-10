package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.support.error.CoreException;

/**
 * ExhibitionCatalogBootSync 단위 검증. 데모 플래그와 무관하게 항상 부팅 시 1회 syncCatalog()를 호출하고,
 * 부팅 적재분의 장르 분류는 별도 스레드로 트리거해 기동(readiness)을 막지 않으며,
 * 실패해도(외부 API 불가·키 미설정 등) 예외를 삼켜 애플리케이션 기동을 막지 않아야 한다.
 */
class ExhibitionCatalogBootSyncTest {

	private ExhibitionFacade exhibitionFacade;
	private CatalogEnricher catalogEnricher;
	private ExhibitionCatalogBootSync bootSync;

	@BeforeEach
	void setUp() {
		exhibitionFacade = mock(ExhibitionFacade.class);
		catalogEnricher = mock(CatalogEnricher.class);
		bootSync = new ExhibitionCatalogBootSync(exhibitionFacade, catalogEnricher);
	}

	@Test
	@DisplayName("run: syncCatalog() 1회 호출 후 장르 분류를 별도 스레드로 트리거한다(cold start 방지)")
	void run_동기화후_장르트리거() {
		given(exhibitionFacade.syncCatalog()).willReturn(2);

		bootSync.run(new DefaultApplicationArguments());

		verify(exhibitionFacade, times(1)).syncCatalog();
		// 장르 분류는 데몬 스레드에서 수행 — 기동을 막지 않도록 비동기라 timeout으로 대기 검증.
		verify(catalogEnricher, timeout(2000).times(1)).enrichGenres();
	}

	@Test
	@DisplayName("run: facade가 예외를 던져도 삼켜서 부팅을 막지 않는다")
	void run_예외삼킴() {
		given(exhibitionFacade.syncCatalog())
				.willThrow(new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 전시 API 호출 실패"));

		assertThatCode(() -> bootSync.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

		verify(exhibitionFacade, times(1)).syncCatalog();
	}
}
