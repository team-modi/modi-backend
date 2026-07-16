package modi.backend.application.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.domain.exhibition.ExhibitionRepository;

/**
 * AdminExhibitionFacade.reparseDescriptions 단위 검증 — 마크업이 남은 설명만 평문으로 정리·저장하고 이미 깨끗한 행은 건너뛴다(멱등).
 */
class AdminExhibitionFacadeTest {

	@Test
	@DisplayName("reparseDescriptions — 태그가 남은 행만 정리·저장하고 깨끗한 행은 건드리지 않는다")
	void reparse_마크업행만_갱신() {
		ExhibitionRepository repo = mock(ExhibitionRepository.class);
		Exhibition markup = catalog("<!-- wp:paragraph --><p style=\"line-height:1.8;\"><span>배민정 작가는 AI에 입력한다.</span></p>");
		Exhibition already = catalog("이미 깨끗한 설명이에요.");
		given(repo.findCatalogWithDescription()).willReturn(List.of(markup, already));
		AdminExhibitionFacade facade = new AdminExhibitionFacade(repo, mock(modi.backend.domain.exhibition.ExhibitionHistoryRepository.class));

		AdminExhibitionResult.DescriptionReparse result = facade.reparseDescriptions();

		assertThat(result.scanned()).isEqualTo(2);
		assertThat(result.updated()).isEqualTo(1);
		assertThat(markup.getDescription()).isEqualTo("배민정 작가는 AI에 입력한다.");
		assertThat(already.getDescription()).isEqualTo("이미 깨끗한 설명이에요.");
		verify(repo, times(1)).save(markup);
		verify(repo, never()).save(already);
	}

	private Exhibition catalog(String description) {
		LocalDate today = LocalDate.now();
		return Exhibition.createCatalog("CAT-" + description.hashCode(), "제목", "장소", today.minusDays(1),
				today.plusDays(10), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, description, null,
				null, null, "기관", null, null, null, "전시", "서울");
	}
}
