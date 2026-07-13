package modi.backend.interfaces.exhibition.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.application.exhibition.ExhibitionResult;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionRegion;

/**
 * 상세 응답 price 폴백 검증 — 원천에 가격이 없으면(null/공백) "관람료 정보 없음"으로 노출하고, 값이 있으면 그대로 보낸다.
 * DB·Result는 null(=미상)을 유지하고 프론트 노출 단계에서만 문구로 대체한다(값을 지어내지 않음).
 */
class ExhibitionDtoPriceTest {

	@Test
	@DisplayName("price가 null이면 '관람료 정보 없음'으로 대체한다")
	void price_null_폴백() {
		ExhibitionDto.DetailResponse response = ExhibitionDto.DetailResponse.from(detailWithPrice(null));

		assertThat(response.price()).isEqualTo("관람료 정보 없음");
	}

	@Test
	@DisplayName("price가 있으면 원본 문구를 그대로 노출한다")
	void price_존재_그대로() {
		ExhibitionDto.DetailResponse response = ExhibitionDto.DetailResponse.from(detailWithPrice("성인 20,000원"));

		assertThat(response.price()).isEqualTo("성인 20,000원");
	}

	private ExhibitionResult.Detail detailWithPrice(String price) {
		LocalDate today = LocalDate.now();
		Exhibition exhibition = Exhibition.createCatalog("CAT-PRICE", "제목", "장소", today.minusDays(1),
				today.plusDays(10), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING, null, "설명", null,
				price, null, "기관", null, null, null, "전시", "서울");
		return ExhibitionResult.Detail.from(exhibition, false, false);
	}
}
