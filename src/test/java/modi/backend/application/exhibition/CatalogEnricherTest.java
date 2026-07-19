package modi.backend.application.exhibition;

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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.application.exhibition.contract.ExhibitionBackfill;
import modi.backend.ingestion.application.draft.ExhibitionDraftFacade;
import modi.backend.ingestion.application.enricher.CatalogEnricher;
import modi.backend.ingestion.application.outbox.ExhibitionOutboxFacade;
import modi.backend.ingestion.config.CatalogEnrichProperties;
import modi.backend.domain.exhibition.genre.GenreProvider;
import modi.backend.domain.exhibition.genre.GenreClassification;
import modi.backend.domain.exhibition.genre.GenreResult;
import modi.backend.ingestion.domain.outbox.OutboxFailureType;
import modi.backend.ingestion.domain.outbox.OutboxMessage;
import modi.backend.ingestion.domain.outbox.OutboxMessageType;
import modi.backend.domain.exhibition.genre.GenreClassificationException;
import modi.backend.domain.exhibition.genre.GenreClassifier;

/**
 * CatalogEnricher 단위 검증 — <b>전시 아웃박스 기반</b> 장르 처리(스윕 → 드레인). 핵심 둘:
 * (1) 대상 해소는 draft 우선·전시 폴백 이원화(ADR-10) — draft는 반영 트랜잭션에서 승격까지 간다.
 * (2) 계약 반전(ADR-11) — 분류기 실패는 폴백값이 아니라 예외이고, 배치 전부를 RETRYABLE로 남겨 회복 후 재분류한다.
 */
class CatalogEnricherTest {

	private final CatalogEnrichProperties props = new CatalogEnrichProperties(40, 20);

	private ExhibitionBackfill backfill;
	private ExhibitionDraftFacade draftFacade;
	private ExhibitionOutboxFacade outboxFacade;
	private GenreClassifier classifier;
	private CatalogEnricher enricher;

	@BeforeEach
	void setUp() {
		backfill = mock(ExhibitionBackfill.class);
		draftFacade = mock(ExhibitionDraftFacade.class);
		outboxFacade = mock(ExhibitionOutboxFacade.class);
		classifier = mock(GenreClassifier.class);
		enricher = new CatalogEnricher(backfill, draftFacade, outboxFacade, props, classifier);
		when(backfill.findUnclassifiedCatalogExternalIds(anyInt())).thenReturn(List.of());
		when(backfill.resolveGenreInputs(anyCollection())).thenReturn(Map.of());
		when(draftFacade.resolveGenreInput(anyString())).thenReturn(Optional.empty());
		when(outboxFacade.findDue(eq(OutboxMessageType.CLASSIFY_GENRE), anyInt(), any())).thenReturn(List.of());
	}

	private static GenreClassification classification() {
		return new GenreClassification("전시", null, null, null, null, null);
	}

	private static OutboxMessage genreMessage(String externalId) {
		return OutboxMessage.enqueue(OutboxMessageType.CLASSIFY_GENRE, externalId, LocalDateTime.now());
	}

	@Test
	@DisplayName("스윕 — 미분류 레거시 CATALOG를 CLASSIFY_GENRE로 멱등 enqueue한다")
	void enrichGenres_미분류를_메시지로_스윕() {
		when(backfill.findUnclassifiedCatalogExternalIds(anyInt())).thenReturn(List.of("E1", "E2"));

		enricher.enrichGenres();

		verify(outboxFacade).enqueueAll(eq(OutboxMessageType.CLASSIFY_GENRE), eq(List.of("E1", "E2")), any());
	}

	@Test
	@DisplayName("드레인 — 전시 폴백 경로: 분류 성공분을 정준층에 쓰고 메시지를 성공 처리한다(배치당 AI 1콜)")
	void enrichGenres_전시경로_성공반영() {
		OutboxMessage m1 = genreMessage("E1");
		OutboxMessage m2 = genreMessage("E2");
		when(outboxFacade.findDue(eq(OutboxMessageType.CLASSIFY_GENRE), anyInt(), any()))
				.thenReturn(List.of(m1, m2), List.of());
		when(backfill.resolveGenreInputs(anyCollection()))
				.thenReturn(Map.of("E1", classification(), "E2", classification()));
		when(classifier.classifyAll(anyList())).thenReturn(List.of(
				GenreResult.ai("사진", GenreProvider.GEMINI, "gemini-2.5-flash"),
				GenreResult.ai("미디어아트", GenreProvider.GEMINI, "gemini-2.5-flash")));

		int total = enricher.enrichGenres();

		verify(classifier, times(1)).classifyAll(anyList()); // 전시마다가 아니라 배치당 1콜
		verify(backfill, times(2)).applyGenreResults(argThat(m -> m.size() == 1), any());
		verify(outboxFacade, times(2)).markSucceeded(any(), any());
		assertThat(total).isEqualTo(2);
	}

	@Test
	@DisplayName("드레인 — draft 우선: draft 대상은 승격 반영 경로로 가고 전시 정준층엔 쓰지 않는다(ADR-10)")
	void enrichGenres_draft경로_승격반영() {
		OutboxMessage m1 = genreMessage("D1");
		when(outboxFacade.findDue(eq(OutboxMessageType.CLASSIFY_GENRE), anyInt(), any()))
				.thenReturn(List.of(m1), List.of());
		when(draftFacade.resolveGenreInput("D1")).thenReturn(Optional.of(classification()));
		when(classifier.classifyAll(anyList())).thenReturn(List.of(
				GenreResult.ai("사진", GenreProvider.GEMINI, "gemini-2.5-flash")));

		enricher.enrichGenres();

		verify(draftFacade).applyGenre(eq("D1"), any(), any()); // 게이트 충족 시 이 트랜잭션에서 승격까지
		verify(backfill, never()).applyGenreResults(any(), any());
		verify(outboxFacade).markSucceeded(eq(m1), any());
	}

	@Test
	@DisplayName("드레인 — 분류기 예외(전 공급자 실패)면 배치 전부를 RETRYABLE로 남기고 아무것도 쓰지 않는다(ADR-11)")
	void enrichGenres_분류실패_전부_재시도() {
		OutboxMessage m1 = genreMessage("E1");
		OutboxMessage m2 = genreMessage("D1");
		when(outboxFacade.findDue(eq(OutboxMessageType.CLASSIFY_GENRE), anyInt(), any()))
				.thenReturn(List.of(m1, m2), List.of());
		when(backfill.resolveGenreInputs(anyCollection())).thenReturn(Map.of("E1", classification()));
		when(draftFacade.resolveGenreInput("D1")).thenReturn(Optional.of(classification()));
		when(classifier.classifyAll(anyList())).thenThrow(new GenreClassificationException("전 공급자 실패"));

		enricher.enrichGenres();

		verify(outboxFacade, times(2)).markFailed(any(), eq(OutboxFailureType.RETRYABLE), anyString(), any());
		verify(outboxFacade, never()).markSucceeded(any(), any());
		verify(backfill, never()).applyGenreResults(any(), any());
		verify(draftFacade, never()).applyGenre(any(), any(), any()); // 가짜 값이 승격을 오염시키지 않는다
	}

	@Test
	@DisplayName("드레인 — draft도 전시도 대상이 아니면(이미 분류/사라짐) AI 없이 메시지를 성공 마감한다")
	void enrichGenres_이미분류_AI없이_마감() {
		OutboxMessage m1 = genreMessage("E1");
		when(outboxFacade.findDue(eq(OutboxMessageType.CLASSIFY_GENRE), anyInt(), any()))
				.thenReturn(List.of(m1), List.of());

		enricher.enrichGenres();

		verify(classifier, never()).classifyAll(anyList()); // 할 일 없으면 AI를 태우지 않는다
		verify(outboxFacade).markSucceeded(eq(m1), any());
	}

	@Test
	@DisplayName("드레인 — 도래 메시지가 없으면 AI를 태우지 않고 즉시 끝낸다")
	void enrichGenres_도래없으면_즉시종료() {
		assertThat(enricher.enrichGenres()).isZero();
		verify(classifier, never()).classifyAll(anyList());
	}
}
