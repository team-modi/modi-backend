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
 * syncCatalog 적재 정책 단위 검증 — 동기화는 <b>신규 전시만 추가</b>하고 기존 행(externalId 중복)은 건드리지 않는다.
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
	@DisplayName("syncCatalog — 신규 externalId만 저장하고, 이미 있는 전시는 건드리지 않는다(재적재 갱신 없음)")
	void syncCatalog_신규만_추가() {
		LocalDate today = LocalDate.now();
		Exhibition existing = Exhibition.createCatalog("CAT-OLD", "기존 전시", "장소", today.minusDays(3),
				today.plusDays(3), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null, null, null,
				null, "기관", null, null, null, "전시", "서울");
		given(catalogClient.fetchAll()).willReturn(List.of(
				data("CAT-OLD", "기존 전시(원천 갱신본)"),
				data("CAT-NEW", "신규 전시")));
		given(exhibitionRepository.findByExternalId("CAT-OLD")).willReturn(Optional.of(existing));
		given(exhibitionRepository.findByExternalId("CAT-NEW")).willReturn(Optional.empty());

		int inserted = facade.syncCatalog();

		assertThat(inserted).isEqualTo(1);
		ArgumentCaptor<Exhibition> captor = ArgumentCaptor.forClass(Exhibition.class);
		verify(exhibitionRepository, times(1)).save(captor.capture());
		assertThat(captor.getValue().getExternalId()).isEqualTo("CAT-NEW");
		// 기존 행은 제목이 원천 갱신본으로 바뀌지 않는다(참조 무변경).
		assertThat(existing.getTitle()).isEqualTo("기존 전시");
	}

	@Test
	@DisplayName("syncCatalog — 기간 비정상(종료<시작) 원천 레코드는 스킵하고 나머지는 적재한다")
	void syncCatalog_기간비정상_스킵() {
		LocalDate today = LocalDate.now();
		CatalogExhibitionData invalid = new CatalogExhibitionData("CAT-BAD", "역전 기간", "장소", today,
				today.minusDays(1), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null, "기관",
				null, null, null, "전시", "서울");
		given(catalogClient.fetchAll()).willReturn(List.of(invalid, data("CAT-OK", "정상 전시")));
		given(exhibitionRepository.findByExternalId("CAT-OK")).willReturn(Optional.empty());

		int inserted = facade.syncCatalog();

		assertThat(inserted).isEqualTo(1);
		verify(exhibitionRepository, times(1)).save(any());
	}

	private CatalogExhibitionData data(String externalId, String title) {
		LocalDate today = LocalDate.now();
		return new CatalogExhibitionData(externalId, title, "장소", today.minusDays(1), today.plusDays(10),
				ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, null, "기관", null, null, null, "전시", "서울");
	}
}
