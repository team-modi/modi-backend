package modi.backend.application.exhibition.contract;

import java.time.LocalDate;

import modi.backend.domain.exhibition.catalog.ExhibitionCategory;
import modi.backend.domain.exhibition.catalog.ExhibitionRegion;
import modi.backend.domain.exhibition.genre.GenreProvider;

/**
 * 전시 등록 입력(코어 소유 어휘) — 수집이 완성한 draft의 필드 스냅샷.
 * 수집 쪽 타입이 경계를 넘지 않도록 원시값·코어 enum만으로 구성한다(ADR-12 역의존 차단).
 */
public record ExhibitionRegistration(
		String externalId,
		String title,
		String placeName,
		ExhibitionRegion region,
		String sigungu,
		Double gpsX,
		Double gpsY,
		LocalDate startDate,
		LocalDate endDate,
		ExhibitionCategory category,
		String posterUrl,
		String detailUrl,
		String serviceName,
		String price,
		String description,
		String imgUrl,
		String placeAddr,
		String placePhone,
		String placeUrl,
		String genreKeyword,
		GenreProvider genreProvider,
		String genreModel) {
}
