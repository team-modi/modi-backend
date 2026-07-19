package modi.backend.application.exhibition;

import modi.backend.ingestion.application.enricher.CatalogEnricher;
import modi.backend.ingestion.application.ExhibitionCatalogBootSync;
import modi.backend.ingestion.application.CatalogSynchronizer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import modi.backend.domain.exhibition.catalog.ExhibitionErrorCode;
import modi.backend.ingestion.domain.SyncTrigger;
import modi.backend.support.error.CoreException;

/**
 * ExhibitionCatalogBootSync 단위 검증. 데모 플래그와 무관하게 부팅 시 1회 동기화(목록+상세)와 장르 분류를
 * <b>별도 데몬 스레드</b>에서 수행해 기동(readiness)을 막지 않으며(상세 API 수백 콜·AI 배치가 붙어 있으므로),
 * 실패해도(외부 API 불가·키 미설정 등) 예외를 삼켜 애플리케이션 기동을 막지 않아야 한다.
 */
class ExhibitionCatalogBootSyncTest {

	private CatalogSynchronizer catalogSynchronizer;
	private CatalogEnricher catalogEnricher;
	private ExhibitionCatalogBootSync bootSync;

	@BeforeEach
	void setUp() {
		catalogSynchronizer = mock(CatalogSynchronizer.class);
		catalogEnricher = mock(CatalogEnricher.class);
		bootSync = new ExhibitionCatalogBootSync(catalogSynchronizer, catalogEnricher);
	}

	@Test
	@DisplayName("run: 동기화(목록+상세)와 장르 분류를 별도 데몬 스레드에서 순서대로 수행한다(cold start 방지·readiness 비차단)")
	void run_동기화후_장르트리거() {
		given(catalogSynchronizer.syncCatalog(SyncTrigger.BOOT)).willReturn(2);

		bootSync.run(new DefaultApplicationArguments());

		// 동기화·장르 분류 모두 데몬 스레드에서 수행 — 비동기라 timeout으로 대기 검증.
		verify(catalogSynchronizer, timeout(2000).times(1)).syncCatalog(SyncTrigger.BOOT);
		verify(catalogEnricher, timeout(2000).times(1)).enrichGenres();
	}

	@Test
	@DisplayName("run: 동기화가 예외를 던져도 삼켜서 부팅을 막지 않는다")
	void run_예외삼킴() {
		given(catalogSynchronizer.syncCatalog(SyncTrigger.BOOT))
				.willThrow(new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 전시 API 호출 실패"));

		assertThatCode(() -> bootSync.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

		verify(catalogSynchronizer, timeout(2000).times(1)).syncCatalog(SyncTrigger.BOOT);
	}
}
