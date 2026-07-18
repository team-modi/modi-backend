package modi.backend.application.exhibition;

import modi.backend.application.exhibition.sync.CatalogEnricher;
import modi.backend.application.exhibition.sync.EnrichmentJobFacade;
import modi.backend.application.exhibition.sync.ExhibitionIngestFacade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.config.CatalogEnrichProperties;
import modi.backend.config.GenreProperties;
import modi.backend.domain.exhibition.enrichment.EnrichmentJob;
import modi.backend.domain.exhibition.genre.GenreClassification;
import modi.backend.domain.exhibition.genre.GenreClassifier;
import modi.backend.domain.exhibition.genre.GenreProvider;
import modi.backend.domain.exhibition.genre.GenreResult;
import modi.backend.domain.exhibition.enrichment.JobFailureType;
import modi.backend.domain.exhibition.enrichment.JobType;

/**
 * CatalogEnricher 단위 검증 — <b>통합 작업큐 기반</b> 장르 보강(스윕 → 드레인). 핵심은 "AI 최소 1회 무조건":
 * AI가 폴백하면(장애) 작업을 성공 처리하지 않고 RETRYABLE로 둬 회복 후 재분류되게 한다. 배치 구조([조회] → tx 밖
 * AI 호출 → [반영·전이])와 조기 종료도 함께 본다.
 */
class CatalogEnricherTest {

	private final CatalogEnrichProperties props = new CatalogEnrichProperties(40, 20);

	private static GenreClassification classification() {
		return new GenreClassification("전시", null, null, null, null, null);
	}

	private static EnrichmentJob genreJob(String externalId) {
		return EnrichmentJob.enqueue(JobType.GENRE_CLASSIFY, externalId, LocalDateTime.now());
	}

	@Test
	@DisplayName("스윕 — 미분류 CATALOG를 GENRE_CLASSIFY로 멱등 enqueue한다")
	void enrichGenres_미분류를_작업으로_스윕() {
		ExhibitionIngestFacade facade = mock(ExhibitionIngestFacade.class);
		EnrichmentJobFacade jobFacade = mock(EnrichmentJobFacade.class);
		GenreClassifier classifier = mock(GenreClassifier.class);
		when(facade.findUnclassifiedCatalogExternalIds(anyInt())).thenReturn(List.of("E1", "E2"));
		when(jobFacade.findDue(eq(JobType.GENRE_CLASSIFY), anyInt(), any())).thenReturn(List.of());
		CatalogEnricher enricher = new CatalogEnricher(facade, jobFacade, props, new GenreProperties("random"),
				classifier);

		enricher.enrichGenres();

		verify(jobFacade).enqueueAll(eq(JobType.GENRE_CLASSIFY), eq(List.of("E1", "E2")), any());
	}

	@Test
	@DisplayName("드레인 — 랜덤 구성에선 분류 성공분을 정준층에 쓰고 작업을 성공 처리한다(배치당 AI 1콜)")
	void enrichGenres_드레인_성공분_반영() {
		ExhibitionIngestFacade facade = mock(ExhibitionIngestFacade.class);
		EnrichmentJobFacade jobFacade = mock(EnrichmentJobFacade.class);
		GenreClassifier classifier = mock(GenreClassifier.class);
		EnrichmentJob j1 = genreJob("E1");
		EnrichmentJob j2 = genreJob("E2");
		when(facade.findUnclassifiedCatalogExternalIds(anyInt())).thenReturn(List.of());
		when(jobFacade.findDue(eq(JobType.GENRE_CLASSIFY), anyInt(), any()))
				.thenReturn(List.of(j1, j2), List.of());
		when(facade.resolveGenreInputs(anyCollection()))
				.thenReturn(Map.of("E1", classification(), "E2", classification()));
		when(classifier.classifyAll(anyList()))
				.thenReturn(List.of(GenreResult.random("사진"), GenreResult.random("미디어아트")));
		CatalogEnricher enricher = new CatalogEnricher(facade, jobFacade, props, new GenreProperties("random"),
				classifier);

		int total = enricher.enrichGenres();

		verify(classifier, times(1)).classifyAll(anyList()); // 전시마다가 아니라 배치당 1콜
		verify(facade).applyGenreResults(argThat(m -> m.size() == 2), any());
		verify(jobFacade, times(2)).markSucceeded(any(), any());
		assertThat(total).isEqualTo(2);
	}

	@Test
	@DisplayName("드레인 — AI(gemini) 폴백이면 정준층에 쓰지 않고 작업을 RETRYABLE로 둔다(AI 회복 후 재분류)")
	void enrichGenres_AI폴백_재시도로_남긴다() {
		ExhibitionIngestFacade facade = mock(ExhibitionIngestFacade.class);
		EnrichmentJobFacade jobFacade = mock(EnrichmentJobFacade.class);
		GenreClassifier classifier = mock(GenreClassifier.class);
		EnrichmentJob j1 = genreJob("E1");
		when(facade.findUnclassifiedCatalogExternalIds(anyInt())).thenReturn(List.of());
		when(jobFacade.findDue(eq(JobType.GENRE_CLASSIFY), anyInt(), any())).thenReturn(List.of(j1), List.of());
		when(facade.resolveGenreInputs(anyCollection())).thenReturn(Map.of("E1", classification()));
		// gemini 구성에서 provider=RANDOM = AI 장애 폴백.
		when(classifier.classifyAll(anyList())).thenReturn(List.of(GenreResult.random("사진")));
		CatalogEnricher enricher = new CatalogEnricher(facade, jobFacade, props, new GenreProperties("gemini"),
				classifier);

		enricher.enrichGenres();

		verify(facade).applyGenreResults(argThat(Map::isEmpty), any()); // 폴백값은 저장하지 않는다
		verify(jobFacade).markFailed(eq(j1), eq(JobFailureType.RETRYABLE), anyString(), any());
		verify(jobFacade, never()).markSucceeded(any(), any());
	}

	@Test
	@DisplayName("드레인 — AI(gemini)가 실제로 분류하면 정준층에 쓰고 성공 처리한다")
	void enrichGenres_AI성공_반영() {
		ExhibitionIngestFacade facade = mock(ExhibitionIngestFacade.class);
		EnrichmentJobFacade jobFacade = mock(EnrichmentJobFacade.class);
		GenreClassifier classifier = mock(GenreClassifier.class);
		EnrichmentJob j1 = genreJob("E1");
		when(facade.findUnclassifiedCatalogExternalIds(anyInt())).thenReturn(List.of());
		when(jobFacade.findDue(eq(JobType.GENRE_CLASSIFY), anyInt(), any())).thenReturn(List.of(j1), List.of());
		when(facade.resolveGenreInputs(anyCollection())).thenReturn(Map.of("E1", classification()));
		when(classifier.classifyAll(anyList()))
				.thenReturn(List.of(GenreResult.ai("사진", GenreProvider.GEMINI, "gemini-2.5-flash")));
		CatalogEnricher enricher = new CatalogEnricher(facade, jobFacade, props, new GenreProperties("gemini"),
				classifier);

		enricher.enrichGenres();

		verify(facade).applyGenreResults(argThat(m -> m.size() == 1), any());
		verify(jobFacade).markSucceeded(eq(j1), any());
		verify(jobFacade, never()).markFailed(any(), any(), anyString(), any());
	}

	@Test
	@DisplayName("드레인 — 이미 분류됐거나 사라진 대상은 AI 없이 작업을 성공 마감한다")
	void enrichGenres_이미분류_AI없이_마감() {
		ExhibitionIngestFacade facade = mock(ExhibitionIngestFacade.class);
		EnrichmentJobFacade jobFacade = mock(EnrichmentJobFacade.class);
		GenreClassifier classifier = mock(GenreClassifier.class);
		EnrichmentJob j1 = genreJob("E1");
		when(facade.findUnclassifiedCatalogExternalIds(anyInt())).thenReturn(List.of());
		when(jobFacade.findDue(eq(JobType.GENRE_CLASSIFY), anyInt(), any())).thenReturn(List.of(j1), List.of());
		when(facade.resolveGenreInputs(anyCollection())).thenReturn(Map.of()); // 해소 실패 = 이미 분류/사라짐
		CatalogEnricher enricher = new CatalogEnricher(facade, jobFacade, props, new GenreProperties("gemini"),
				classifier);

		enricher.enrichGenres();

		verify(classifier, never()).classifyAll(anyList()); // 할 일 없으면 AI를 태우지 않는다
		verify(jobFacade).markSucceeded(eq(j1), any());
	}

	@Test
	@DisplayName("드레인 — 도래 작업이 없으면 AI를 태우지 않고 즉시 끝낸다")
	void enrichGenres_도래없으면_즉시종료() {
		ExhibitionIngestFacade facade = mock(ExhibitionIngestFacade.class);
		EnrichmentJobFacade jobFacade = mock(EnrichmentJobFacade.class);
		GenreClassifier classifier = mock(GenreClassifier.class);
		when(facade.findUnclassifiedCatalogExternalIds(anyInt())).thenReturn(List.of());
		when(jobFacade.findDue(eq(JobType.GENRE_CLASSIFY), anyInt(), any())).thenReturn(List.of());
		CatalogEnricher enricher = new CatalogEnricher(facade, jobFacade, props, new GenreProperties("random"),
				classifier);

		assertThat(enricher.enrichGenres()).isZero();
		verify(classifier, never()).classifyAll(anyList());
	}
}
