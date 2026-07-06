package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.ExhibitionErrorCode;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * ExhibitionFacade.getDetail 단위 검증(Mockito). 최초 CATALOG 조회 시 detail2 지연수집·캐시 + 조회수 증가를
 * 다룬다. 외부 I/O({@link ExhibitionCatalogClient})가 끼는 Facade라 컨벤션상 Mockito 단위로 대체한다.
 */
class ExhibitionDetailTest {

	private ExhibitionRepository exhibitionRepository;
	private ExhibitionCatalogClient catalogClient;
	private ExhibitionFacade facade;

	@BeforeEach
	void setUp() {
		exhibitionRepository = mock(ExhibitionRepository.class);
		catalogClient = mock(ExhibitionCatalogClient.class);
		facade = new ExhibitionFacade(exhibitionRepository, catalogClient);
		given(exhibitionRepository.save(any(Exhibition.class))).willAnswer(invocation -> invocation.getArgument(0));
	}

	private Exhibition catalogNotSynced(String externalId) {
		return Exhibition.createCatalog(externalId, "제목", "장소", null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null);
	}

	@Test
	@DisplayName("상세 최초조회시 detail2수집 후 캐시 및 조회수증가")
	void 상세_최초조회시_detail2수집_후_캐시_및_조회수증가() {
		Exhibition e = catalogNotSynced("S1");
		given(exhibitionRepository.findById(1L)).willReturn(Optional.of(e));
		given(catalogClient.fetchDetail("S1"))
				.willReturn(Optional.of(new CatalogDetailData("무료", null, null, null, null, null, "주소", null)));

		facade.getDetail(new ExhibitionCriteria.Detail(1L, null));

		verify(catalogClient).fetchDetail("S1");
		assertThat(e.getPlaceAddr()).isEqualTo("주소");
		assertThat(e.getOurViewCount()).isEqualTo(1);

		// 2번째 호출: 이미 synced → fetchDetail 추가 호출 없음
		facade.getDetail(new ExhibitionCriteria.Detail(1L, null));

		verify(catalogClient, times(1)).fetchDetail("S1");
		assertThat(e.getOurViewCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("외부 수집 실패해도 기본 필드로 진행하고 조회수는 증가한다(재시도용 detailSyncedAt은 null 유지)")
	void 상세_외부수집실패시_기본필드로_진행하고_조회수증가() {
		Exhibition e = catalogNotSynced("S2");
		given(exhibitionRepository.findById(2L)).willReturn(Optional.of(e));
		given(catalogClient.fetchDetail("S2"))
				.willThrow(new CoreException(ExhibitionErrorCode.EXTERNAL_API_UNAVAILABLE, "외부 전시 API 호출 실패"));

		assertThatCode(() -> facade.getDetail(new ExhibitionCriteria.Detail(2L, null)))
				.doesNotThrowAnyException();

		assertThat(e.isDetailSynced()).isFalse();
		assertThat(e.getPlaceAddr()).isNull();
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
		Exhibition custom = Exhibition.createCustom(10L, "개인 전시", "장소", null, null, null, null, null, null, null);
		given(exhibitionRepository.findById(3L)).willReturn(Optional.of(custom));

		assertThatThrownBy(() -> facade.getDetail(new ExhibitionCriteria.Detail(3L, 20L)))
				.isInstanceOf(CoreException.class)
				.satisfies(ex -> assertThat(((CoreException) ex).errorCode()).isEqualTo(ErrorType.FORBIDDEN));

		verify(catalogClient, never()).fetchDetail(any());
	}
}
