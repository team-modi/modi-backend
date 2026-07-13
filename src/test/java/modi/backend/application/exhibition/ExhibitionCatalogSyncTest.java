package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

import modi.backend.domain.bookmark.ExhibitionBookmarkRepository;
import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.CatalogExhibitionData;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.domain.exhibition.GenreClassifier;
import modi.backend.domain.venue.VenueRepository;
import modi.backend.infra.record.RecordJpaRepository;

/**
 * syncCatalog 적재 정책 단위 검증 — 동기화는 목록+상세를 한 패스로 채워 <b>적재 시점에 완전한 행</b>으로 만든다:
 * 신규는 상세까지 채워 새로 적재하고, 기존이나 상세 미완성 행은 상세만 채워 완성하며(장르 등 다른 값은 무변경),
 * 이미 상세까지 완성된 행은 건드리지 않는다(외부 상세 호출 없음).
 * 장르 생성은 동기화 직후 CatalogEnricher가 미분류(=신규) 행만 분류한다(스케줄러 테스트에서 검증).
 */
@ExtendWith(MockitoExtension.class)
class ExhibitionCatalogSyncTest {

	@Mock
	ExhibitionRepository exhibitionRepository;
	@Mock
	ExhibitionCatalogClient catalogClient;
	@Mock
	ExhibitionBookmarkRepository exhibitionBookmarkRepository;
	@Mock
	VenueRepository venueRepository;
	@Mock
	RecordJpaRepository recordJpaRepository;
	@Mock
	GenreClassifier genreClassifier;

	@InjectMocks
	ExhibitionFacade facade;

	@Test
	@DisplayName("syncCatalog — 신규는 상세까지 채워 적재하고, 기존 미완성은 상세만 채워 완성한다(목록 필드·제목은 무변경)")
	void syncCatalog_신규적재_기존상세완성() {
		LocalDate today = LocalDate.now();
		Exhibition existing = Exhibition.createCatalog("CAT-OLD", "기존 전시", "장소", today.minusDays(3),
				today.plusDays(3), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null, null, null,
				null, "기관", null, null, null, "전시", "서울");
		given(catalogClient.fetchAll()).willReturn(List.of(
				data("CAT-OLD", "기존 전시(원천 갱신본)"),
				data("CAT-NEW", "신규 전시")));
		given(exhibitionRepository.findByExternalId("CAT-OLD")).willReturn(Optional.of(existing));
		given(exhibitionRepository.findByExternalId("CAT-NEW")).willReturn(Optional.empty());
		// 기존(상세 미완성)은 상세만 채워 완성, 신규는 상세까지 받아 적재.
		given(catalogClient.fetchDetail("CAT-OLD")).willReturn(Optional.of(detail("무료")));
		given(catalogClient.fetchDetail("CAT-NEW")).willReturn(Optional.of(detail("15,000원")));

		int inserted = facade.syncCatalog();

		// 신규만 적재 수에 잡힌다(기존 상세 완성은 별도).
		assertThat(inserted).isEqualTo(1);
		ArgumentCaptor<Exhibition> captor = ArgumentCaptor.forClass(Exhibition.class);
		verify(exhibitionRepository, times(2)).save(captor.capture()); // 기존 완성 + 신규 적재
		// 기존 행: 제목은 원천 갱신본으로 안 바뀌고(재적재 갱신 없음), 상세(price)만 채워져 완성 처리된다.
		assertThat(existing.getTitle()).isEqualTo("기존 전시");
		assertThat(existing.getPrice()).isEqualTo("무료");
		assertThat(existing.isDetailSynced()).isTrue();
		// 신규 행: 상세까지 채운 완전한 행으로 저장된다.
		Exhibition created = captor.getAllValues().stream()
				.filter(e -> "CAT-NEW".equals(e.getExternalId())).findFirst().orElseThrow();
		assertThat(created.getPrice()).isEqualTo("15,000원");
		assertThat(created.isDetailSynced()).isTrue();
	}

	@Test
	@DisplayName("syncCatalog — 이미 상세까지 완성된 기존 행은 상세 재호출·저장 없이 건너뛴다")
	void syncCatalog_완성된기존_스킵() {
		LocalDate today = LocalDate.now();
		Exhibition synced = Exhibition.createCatalog("CAT-DONE", "완성 전시", "장소", today.minusDays(1),
				today.plusDays(5), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null, null, null,
				null, "기관", null, null, null, "전시", "서울");
		synced.applyDetail(detail("무료")); // detailSyncedAt 세팅 → 완성 상태
		given(catalogClient.fetchAll()).willReturn(List.of(data("CAT-DONE", "완성 전시")));
		given(exhibitionRepository.findByExternalId("CAT-DONE")).willReturn(Optional.of(synced));

		int inserted = facade.syncCatalog();

		assertThat(inserted).isZero();
		verify(exhibitionRepository, times(0)).save(any());
		verify(catalogClient, times(0)).fetchDetail(any()); // 완성 행엔 상세 API를 다시 부르지 않는다
	}

	@Test
	@DisplayName("syncCatalog — 기간 비정상(종료<시작) 원천 레코드는 스킵하고 나머지는 상세까지 채워 적재한다")
	void syncCatalog_기간비정상_스킵() {
		LocalDate today = LocalDate.now();
		CatalogExhibitionData invalid = new CatalogExhibitionData("CAT-BAD", "역전 기간", "장소", today,
				today.minusDays(1), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null, "기관",
				null, null, null, "전시", "서울");
		given(catalogClient.fetchAll()).willReturn(List.of(invalid, data("CAT-OK", "정상 전시")));
		given(exhibitionRepository.findByExternalId("CAT-OK")).willReturn(Optional.empty());
		given(catalogClient.fetchDetail("CAT-OK")).willReturn(Optional.empty()); // 원천 상세 없음 → 목록만으로 적재

		int inserted = facade.syncCatalog();

		assertThat(inserted).isEqualTo(1);
		verify(exhibitionRepository, times(1)).save(any());
	}

	private CatalogExhibitionData data(String externalId, String title) {
		LocalDate today = LocalDate.now();
		return new CatalogExhibitionData(externalId, title, "장소", today.minusDays(1), today.plusDays(10),
				ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null, "기관", null, null, null, "전시", "서울");
	}

	private CatalogDetailData detail(String price) {
		return new CatalogDetailData(price, "전시 소개", "https://example.com/detail", "02-000-0000",
				"https://example.com/img.jpg", "https://example.com/place", "서울시 종로구", "PLACE-1");
	}
}
