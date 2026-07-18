package modi.backend.application.exhibition;

import modi.backend.application.exhibition.sync.EnrichmentJobFacade;
import modi.backend.application.exhibition.sync.ExhibitionIngestFacade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import modi.backend.domain.bookmark.ExhibitionBookmarkRepository;
import modi.backend.domain.exhibition.catalog.ArtistRepository;
import modi.backend.domain.exhibition.sync.CatalogDetailData;
import modi.backend.domain.exhibition.sync.CatalogExhibitionData;
import modi.backend.domain.exhibition.sync.CatalogListData;
import modi.backend.infra.exhibition.sync.CultureDetailResponseJpaRepository;
import modi.backend.infra.exhibition.sync.CultureListResponseJpaRepository;
import modi.backend.domain.exhibition.catalog.Exhibition;
import modi.backend.domain.exhibition.sync.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.catalog.ExhibitionCategory;
import modi.backend.domain.exhibition.catalog.ExhibitionPlace;
import modi.backend.domain.exhibition.catalog.ExhibitionPlaceRepository;
import modi.backend.domain.exhibition.catalog.ExhibitionRegion;
import modi.backend.domain.exhibition.catalog.ExhibitionQueryRepository;
import modi.backend.domain.exhibition.catalog.ExhibitionRepository;
import modi.backend.domain.exhibition.genre.GenreClassifier;
import modi.backend.infra.exhibition.hours.GooglePlaceResponseJpaRepository;
import modi.backend.domain.exhibition.sync.SyncRunRepository;
import modi.backend.domain.venue.VenueRepository;
import modi.backend.infra.record.RecordJpaRepository;

/**
 * syncCatalog 적재 정책 단위 검증 — 목록+상세를 한 패스로 채운다: 신규는 전시장 resolve 후 상세까지 채워 적재하고,
 * 기존 미완성은 상세만 채워 완성하며(상세 satellite 생성), 이미 상세행이 있는 기존은 상세 재호출·저장 없이 건너뛴다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExhibitionCatalogSyncTest {

	@Mock ExhibitionRepository exhibitionRepository;
	@Mock ExhibitionCatalogClient catalogClient;
	@Mock ExhibitionBookmarkRepository exhibitionBookmarkRepository;
	@Mock VenueRepository venueRepository;
	@Mock RecordJpaRepository recordJpaRepository;
	@Mock GooglePlaceResponseJpaRepository googlePlaceResponseRepository;
	@Mock GenreClassifier genreClassifier;
	@Mock CultureListResponseJpaRepository cultureListResponseRepository;
	@Mock CultureDetailResponseJpaRepository cultureDetailResponseRepository;
	@Mock SyncRunRepository syncRunRepository;
	@Mock ExhibitionPlaceRepository exhibitionPlaceRepository;
	@Mock ExhibitionQueryRepository exhibitionQueryRepository;
	@Mock ArtistRepository artistRepository;
	@Mock EnrichmentJobFacade enrichmentJobFacade;

	@InjectMocks
	ExhibitionIngestFacade facade;

	private void stubPlaceResolveCreatesNew() {
		given(exhibitionPlaceRepository.resolveOrCreate(any(), any(), any(), any(), any()))
				.willAnswer(inv -> withPlaceId(ExhibitionPlace.createFromList(inv.getArgument(0),
						inv.getArgument(1), inv.getArgument(2), inv.getArgument(3), inv.getArgument(4)), 55L));
		given(exhibitionPlaceRepository.findById(anyLong())).willReturn(Optional.empty());
		given(exhibitionRepository.save(any(Exhibition.class))).willAnswer(inv -> inv.getArgument(0));
	}

	@Test
	@DisplayName("syncCatalog — 신규는 전시장 resolve 후 상세까지 채워 적재하고, 기존 미완성은 상세만 채워 완성한다")
	void syncCatalog_신규적재_기존상세완성() {
		LocalDate today = LocalDate.now();
		stubPlaceResolveCreatesNew();
		Exhibition existing = withId(Exhibition.createCatalog("CAT-OLD", "기존 전시", 10L, today.minusDays(3),
				today.plusDays(3), ExhibitionCategory.PAINTING, null, null, "기관"), 100L);
		given(catalogClient.fetchAll()).willReturn(listData(List.of(
				data("CAT-OLD", "기존 전시(원천 갱신본)"), data("CAT-NEW", "신규 전시"))));
		given(exhibitionRepository.findByExternalId("CAT-OLD")).willReturn(Optional.of(existing));
		given(exhibitionRepository.findByExternalId("CAT-NEW")).willReturn(Optional.empty());
		given(exhibitionRepository.hasDetail(100L)).willReturn(false);
		given(catalogClient.fetchDetail("CAT-OLD")).willReturn(Optional.of(detail("무료")));
		given(catalogClient.fetchDetail("CAT-NEW")).willReturn(Optional.of(detail("15,000원")));

		int inserted = facade.syncCatalog();

		assertThat(inserted).isEqualTo(1); // 신규만 적재 수에 잡힌다(기존 상세 완성은 별도)
		assertThat(existing.getTitle()).isEqualTo("기존 전시"); // 제목은 원천 갱신본으로 안 바뀐다
		ArgumentCaptor<String> priceCaptor = ArgumentCaptor.forClass(String.class);
		verify(exhibitionRepository, times(2)).applyDetail(anyLong(), priceCaptor.capture(), any(), any(), any());
		assertThat(priceCaptor.getAllValues()).containsExactlyInAnyOrder("무료", "15,000원");
	}

	@Test
	@DisplayName("syncCatalog — 이미 상세행이 있는 기존은 상세 재호출·저장 없이 건너뛴다")
	void syncCatalog_완성된기존_스킵() {
		LocalDate today = LocalDate.now();
		Exhibition synced = withId(Exhibition.createCatalog("CAT-DONE", "완성 전시", 10L, today.minusDays(1),
				today.plusDays(5), ExhibitionCategory.PAINTING, null, null, "기관"), 200L);
		given(catalogClient.fetchAll()).willReturn(listData(List.of(data("CAT-DONE", "완성 전시"))));
		given(exhibitionRepository.findByExternalId("CAT-DONE")).willReturn(Optional.of(synced));
		given(exhibitionRepository.hasDetail(200L)).willReturn(true); // 상세행 존재 → 완성 상태

		int inserted = facade.syncCatalog();

		assertThat(inserted).isZero();
		verify(catalogClient, never()).fetchDetail(any());
		verify(exhibitionRepository, never()).applyDetail(anyLong(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("syncCatalog — 기간 비정상(종료<시작) 원천 레코드는 스킵하고 나머지는 상세까지 채워 적재한다")
	void syncCatalog_기간비정상_스킵() {
		LocalDate today = LocalDate.now();
		stubPlaceResolveCreatesNew();
		CatalogExhibitionData invalid = new CatalogExhibitionData("CAT-BAD", "역전 기간", "장소", today,
				today.minusDays(1), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null, "기관",
				null, null, null, "전시", "서울", null);
		given(catalogClient.fetchAll()).willReturn(listData(List.of(invalid, data("CAT-OK", "정상 전시"))));
		given(exhibitionRepository.findByExternalId("CAT-OK")).willReturn(Optional.empty());
		given(exhibitionRepository.hasDetail(anyLong())).willReturn(false);
		given(catalogClient.fetchDetail("CAT-OK")).willReturn(Optional.empty()); // 원천 상세 없음 → 확인 완료행만

		int inserted = facade.syncCatalog();

		assertThat(inserted).isEqualTo(1);
		verify(exhibitionRepository, times(1)).save(any());
	}

	private static Exhibition withId(Exhibition e, long id) {
		ReflectionTestUtils.setField(e, "id", id);
		return e;
	}

	private static ExhibitionPlace withPlaceId(ExhibitionPlace p, long id) {
		ReflectionTestUtils.setField(p, "id", id);
		return p;
	}

	private static CatalogListData listData(java.util.List<CatalogExhibitionData> items) {
		return new CatalogListData(items, items.size(), false);
	}

	private CatalogExhibitionData data(String externalId, String title) {
		LocalDate today = LocalDate.now();
		return new CatalogExhibitionData(externalId, title, "장소", today.minusDays(1), today.plusDays(10),
				ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null, "기관", null, null, null, "전시", "서울",
				null);
	}

	private CatalogDetailData detail(String price) {
		return new CatalogDetailData(price, "전시 소개", "https://example.com/detail", "02-000-0000",
				"https://example.com/img.jpg", "https://example.com/place", "서울시 종로구", "PLACE-1", null);
	}
}
