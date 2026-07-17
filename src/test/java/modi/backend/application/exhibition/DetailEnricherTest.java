package modi.backend.application.exhibition;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.config.EnrichmentProperties;
import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.EnrichmentJob;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.domain.exhibition.JobFailureType;
import modi.backend.domain.exhibition.JobType;
import modi.backend.support.error.CoreException;

/**
 * DetailEnricher 단위 검증 — <b>현행 최대 갭 해소</b>의 핵심: 상세 재시도 선별을 통합 작업큐 <b>읽기</b>로 배선한다.
 * 도래한 DETAIL_SYNC 작업만 집어 상세를 재조회하고, 결과에 따라 성공/재시도로 전이한다. 외부 호출은 트랜잭션 밖이다.
 */
class DetailEnricherTest {

	private final EnrichmentProperties props = new EnrichmentProperties(5, 60L, 3600L, 50, 60000L, 30);

	private static EnrichmentJob detailJob(String externalId) {
		return EnrichmentJob.enqueue(JobType.DETAIL_SYNC, externalId, LocalDateTime.now());
	}

	private static CatalogDetailData detail() {
		return new CatalogDetailData("무료", "설명", null, null, null, null, "서울시 종로구", "PLACE-1", null);
	}

	@Test
	@DisplayName("선별은 작업 읽기로 — 도래한 DETAIL_SYNC만 집어 상세를 채우고 성공 처리한다")
	void 상세필요_채우고_성공() {
		EnrichmentJobFacade jobFacade = mock(EnrichmentJobFacade.class);
		ExhibitionFacade facade = mock(ExhibitionFacade.class);
		ExhibitionCatalogClient client = mock(ExhibitionCatalogClient.class);
		EnrichmentJob job = detailJob("E1");
		when(jobFacade.findDue(eq(JobType.DETAIL_SYNC), anyInt(), any())).thenReturn(List.of(job));
		when(facade.findDetailTargetState("E1")).thenReturn(DetailTargetState.NEEDS_DETAIL);
		when(client.fetchDetail("E1")).thenReturn(Optional.of(detail()));
		DetailEnricher enricher = new DetailEnricher(jobFacade, facade, client, props);

		enricher.enrichDetails();

		verify(facade).applyDetailForJob(eq("E1"), any());
		verify(jobFacade).markSucceeded(eq(job), any());
	}

	@Test
	@DisplayName("상세 조회가 일시 실패하면 RETRYABLE로 기록한다(timeout/5xx류 → 백오프 재시도)")
	void 상세실패_재시도기록() {
		EnrichmentJobFacade jobFacade = mock(EnrichmentJobFacade.class);
		ExhibitionFacade facade = mock(ExhibitionFacade.class);
		ExhibitionCatalogClient client = mock(ExhibitionCatalogClient.class);
		EnrichmentJob job = detailJob("E1");
		when(jobFacade.findDue(eq(JobType.DETAIL_SYNC), anyInt(), any())).thenReturn(List.of(job));
		when(facade.findDetailTargetState("E1")).thenReturn(DetailTargetState.NEEDS_DETAIL);
		when(client.fetchDetail("E1"))
				.thenThrow(new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 API 실패"));
		DetailEnricher enricher = new DetailEnricher(jobFacade, facade, client, props);

		enricher.enrichDetails();

		verify(jobFacade).markFailed(eq(job), eq(JobFailureType.RETRYABLE), any(), any());
		verify(facade, never()).applyDetailForJob(any(), any());
	}

	@Test
	@DisplayName("이미 다른 경로가 상세를 채웠으면 외부 호출 없이 성공 처리한다")
	void 이미완성_성공마감() {
		EnrichmentJobFacade jobFacade = mock(EnrichmentJobFacade.class);
		ExhibitionFacade facade = mock(ExhibitionFacade.class);
		ExhibitionCatalogClient client = mock(ExhibitionCatalogClient.class);
		EnrichmentJob job = detailJob("E1");
		when(jobFacade.findDue(eq(JobType.DETAIL_SYNC), anyInt(), any())).thenReturn(List.of(job));
		when(facade.findDetailTargetState("E1")).thenReturn(DetailTargetState.ALREADY_SYNCED);
		DetailEnricher enricher = new DetailEnricher(jobFacade, facade, client, props);

		enricher.enrichDetails();

		verify(client, never()).fetchDetail(any());
		verify(jobFacade).markSucceeded(eq(job), any());
	}

	@Test
	@DisplayName("전시가 아직 없으면(신규 상세실패분) RETRYABLE로 두어 다음 동기화 후 재처리되게 한다")
	void 전시미적재_재시도로_남긴다() {
		EnrichmentJobFacade jobFacade = mock(EnrichmentJobFacade.class);
		ExhibitionFacade facade = mock(ExhibitionFacade.class);
		ExhibitionCatalogClient client = mock(ExhibitionCatalogClient.class);
		EnrichmentJob job = detailJob("E1");
		when(jobFacade.findDue(eq(JobType.DETAIL_SYNC), anyInt(), any())).thenReturn(List.of(job));
		when(facade.findDetailTargetState("E1")).thenReturn(DetailTargetState.MISSING);
		DetailEnricher enricher = new DetailEnricher(jobFacade, facade, client, props);

		enricher.enrichDetails();

		verify(client, never()).fetchDetail(any());
		verify(jobFacade).markFailed(eq(job), eq(JobFailureType.RETRYABLE), any(), any());
	}

	@Test
	@DisplayName("도래 작업이 없으면 외부 호출 없이 끝낸다")
	void 도래없음_무호출() {
		EnrichmentJobFacade jobFacade = mock(EnrichmentJobFacade.class);
		ExhibitionFacade facade = mock(ExhibitionFacade.class);
		ExhibitionCatalogClient client = mock(ExhibitionCatalogClient.class);
		when(jobFacade.findDue(eq(JobType.DETAIL_SYNC), anyInt(), any())).thenReturn(List.of());
		DetailEnricher enricher = new DetailEnricher(jobFacade, facade, client, props);

		enricher.enrichDetails();

		verify(client, never()).fetchDetail(any());
	}
}
