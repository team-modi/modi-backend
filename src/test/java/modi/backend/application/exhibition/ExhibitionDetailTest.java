package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import modi.backend.domain.bookmark.ExhibitionBookmarkRepository;
import modi.backend.domain.exhibition.ArtistRepository;
import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.CultureDetailResponseRepository;
import modi.backend.domain.exhibition.CultureListResponseRepository;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionArtistRepository;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionDetail;
import modi.backend.domain.exhibition.ExhibitionDetailRepository;
import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.domain.exhibition.ExhibitionGenreRepository;
import modi.backend.domain.exhibition.ExhibitionPlace;
import modi.backend.domain.exhibition.ExhibitionPlaceRepository;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.domain.exhibition.GooglePlaceResponseRepository;
import modi.backend.domain.exhibition.PlaceHoursRepository;
import modi.backend.domain.exhibition.SyncRunRepository;
import modi.backend.infra.record.RecordJpaRepository;
import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * ExhibitionFacade.getDetail 단위 검증(Mockito). 최초 CATALOG 조회 시 상세 지연수집(상세 satellite 생성)·조회수 증가를 다룬다.
 * 상세 부재 판정이 exhibition_detail 행 존재로 바뀌었다(applyDetail 지연 mutation 소멸).
 */
class ExhibitionDetailTest {

	private ExhibitionRepository exhibitionRepository;
	private ExhibitionCatalogClient catalogClient;
	private ExhibitionBookmarkRepository bookmarkRepository;
	private modi.backend.domain.venue.VenueRepository venueRepository;
	private RecordJpaRepository recordJpaRepository;
	private ExhibitionPlaceRepository placeRepository;
	private ExhibitionDetailRepository detailRepository;
	private PlaceHoursRepository placeHoursRepository;
	private ExhibitionArtistRepository exhibitionArtistRepository;
	private ExhibitionGenreRepository exhibitionGenreRepository;
	private ExhibitionFacade facade;

	@BeforeEach
	void setUp() {
		exhibitionRepository = mock(ExhibitionRepository.class);
		catalogClient = mock(ExhibitionCatalogClient.class);
		bookmarkRepository = mock(ExhibitionBookmarkRepository.class);
		venueRepository = mock(modi.backend.domain.venue.VenueRepository.class);
		recordJpaRepository = mock(RecordJpaRepository.class);
		placeRepository = mock(ExhibitionPlaceRepository.class);
		detailRepository = mock(ExhibitionDetailRepository.class);
		placeHoursRepository = mock(PlaceHoursRepository.class);
		exhibitionArtistRepository = mock(ExhibitionArtistRepository.class);
		exhibitionGenreRepository = mock(ExhibitionGenreRepository.class);
		facade = new ExhibitionFacade(exhibitionRepository, catalogClient, bookmarkRepository, venueRepository,
				recordJpaRepository, placeHoursRepository, mock(GooglePlaceResponseRepository.class),
				new modi.backend.infra.genre.RandomGenreClassifier(), exhibitionGenreRepository,
				mock(CultureListResponseRepository.class), mock(CultureDetailResponseRepository.class),
				mock(SyncRunRepository.class), placeRepository, detailRepository, mock(ArtistRepository.class),
				exhibitionArtistRepository, mock(EnrichmentJobFacade.class));
		given(exhibitionRepository.save(any(Exhibition.class))).willAnswer(invocation -> invocation.getArgument(0));
		given(placeRepository.findById(anyLong())).willReturn(Optional.of(
				ExhibitionPlace.createFromList("장소", null, null, null, null)));
		given(placeHoursRepository.findByExhibitionPlaceId(anyLong())).willReturn(Optional.empty());
		given(exhibitionArtistRepository.findArtistNames(anyLong())).willReturn(java.util.List.of());
		given(exhibitionGenreRepository.findByExhibitionId(anyLong())).willReturn(Optional.empty());
	}

	private Exhibition catalog(String externalId, long id) {
		Exhibition e = Exhibition.createCatalog(externalId, "제목", 5L, null, null, null, null, null, "기관");
		ReflectionTestUtils.setField(e, "id", id);
		return e;
	}

	@Test
	@DisplayName("상세 최초조회시 상세수집 후 상세행 생성 및 조회수증가")
	void 상세_최초조회시_상세수집_후_상세행생성_및_조회수증가() {
		Exhibition e = catalog("S1", 1L);
		given(exhibitionRepository.findById(1L)).willReturn(Optional.of(e));
		given(detailRepository.existsByExhibitionId(1L)).willReturn(false).willReturn(true);
		given(detailRepository.findByExhibitionId(1L)).willReturn(Optional.empty());
		given(catalogClient.fetchDetail("S1"))
				.willReturn(Optional.of(new CatalogDetailData("무료", null, null, null, null, null, "주소", null, null)));

		facade.getDetail(new ExhibitionCriteria.Detail(1L, null));

		verify(catalogClient).fetchDetail("S1");
		verify(detailRepository).save(any(ExhibitionDetail.class)); // 상세행 생성
		assertThat(e.getOurViewCount()).isEqualTo(1);

		// 2번째 호출: 이미 상세행 존재 → fetchDetail 추가 호출 없음
		facade.getDetail(new ExhibitionCriteria.Detail(1L, null));

		verify(catalogClient, times(1)).fetchDetail("S1");
		assertThat(e.getOurViewCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("외부 수집 실패해도 기본 필드로 진행하고 조회수는 증가한다(상세행 미생성 → 다음 조회에서 재시도)")
	void 상세_외부수집실패시_기본필드로_진행하고_조회수증가() {
		Exhibition e = catalog("S2", 2L);
		given(exhibitionRepository.findById(2L)).willReturn(Optional.of(e));
		given(detailRepository.existsByExhibitionId(2L)).willReturn(false);
		given(catalogClient.fetchDetail("S2"))
				.willThrow(new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 전시 API 호출 실패"));

		assertThatCode(() -> facade.getDetail(new ExhibitionCriteria.Detail(2L, null)))
				.doesNotThrowAnyException();

		verify(detailRepository, never()).save(any());
		assertThat(e.getOurViewCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("존재하지 않는 전시 조회 시 404")
	void 상세_존재하지않으면_404() {
		given(exhibitionRepository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> facade.getDetail(new ExhibitionCriteria.Detail(99L, null)))
				.isInstanceOf(CoreException.class)
				.satisfies(ex -> assertThat(((CoreException) ex).errorCode())
						.isEqualTo(ExhibitionErrorCode.EXHIBITION_NOT_FOUND));

		verify(catalogClient, never()).fetchDetail(any());
	}

	@Test
	@DisplayName("타인의 CUSTOM 전시 조회 시 403")
	void 상세_타인의_CUSTOM_403() {
		Exhibition custom = Exhibition.createCustom(10L, "개인 전시", 5L, null, null, null, null, null, null);
		given(exhibitionRepository.findById(3L)).willReturn(Optional.of(custom));

		assertThatThrownBy(() -> facade.getDetail(new ExhibitionCriteria.Detail(3L, 20L)))
				.isInstanceOf(CoreException.class)
				.satisfies(ex -> assertThat(((CoreException) ex).errorCode()).isEqualTo(ErrorType.FORBIDDEN));

		verify(catalogClient, never()).fetchDetail(any());
	}
}
