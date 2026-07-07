package modi.backend.application.exhibition;

import java.time.LocalDate;
import java.util.List;

import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionFormat;
import modi.backend.domain.exhibition.ExhibitionRegion;

/**
 * 전시 유스케이스 출력 모음. (Facade는 Result까지만)
 * 결측 필드(좌표·썸네일·설명·운영시간·가격 등)는 원천 특성상 null로 내린다.
 */
public final class ExhibitionResult {

	private ExhibitionResult() {
	}

	/** 목록 항목(03_전시.md 3.3.1 content[]). */
	public record ListItem(Long exhibitionId, String type, String title, String posterUrl,
			LocalDate startDate, LocalDate endDate, String place, String region, String category) {

		public static ListItem from(Exhibition exhibition) {
			return new ListItem(exhibition.getId(), exhibition.getType().name(), exhibition.getTitle(),
					exhibition.getPosterUrl(), exhibition.getStartDate(), exhibition.getEndDate(),
					exhibition.getPlace(), name(exhibition.getRegion()), name(exhibition.getCategory()));
		}
	}

	/**
	 * 전시 상세(03_전시.md 3.3.2). artists·keywords는 원천 API(한눈에보는문화정보)가 제공하지 않아
	 * 현재 항상 빈 배열이다 — 04_전시_구현.md 오픈 질문 참고.
	 */
	public record Detail(Long exhibitionId, String type, String title, String posterUrl,
			LocalDate startDate, LocalDate endDate, String place, String region, String category, String format,
			String description, String operatingHours, String price, List<String> artists, List<String> keywords,
			String serviceName, String detailUrl, Double gpsX, Double gpsY,
			String address, String imgUrl, String phone, long viewCount, String sigungu, String placeUrl) {

		public static Detail from(Exhibition exhibition) {
			List<String> artists = exhibition.getArtist() == null || exhibition.getArtist().isBlank()
					? List.of() : List.of(exhibition.getArtist());
			return new Detail(exhibition.getId(), exhibition.getType().name(), exhibition.getTitle(),
					exhibition.getPosterUrl(), exhibition.getStartDate(), exhibition.getEndDate(),
					exhibition.getPlace(), name(exhibition.getRegion()), name(exhibition.getCategory()),
					name(exhibition.getFormat()),
					exhibition.getDescription(), exhibition.getOperatingHours(), exhibition.getPrice(),
					artists, List.of(), exhibition.getServiceName(), exhibition.getDetailUrl(),
					exhibition.getGpsX(), exhibition.getGpsY(),
					exhibition.getPlaceAddr(), exhibition.getImgUrl(), exhibition.getPhone(),
					exhibition.getOurViewCount(), exhibition.getSigungu(), exhibition.getPlaceUrl());
		}
	}

	/** 개인 전시 등록 결과(03_전시.md 3.3.3). */
	public record Created(Long exhibitionId, String type) {

		public static Created from(Exhibition exhibition) {
			return new Created(exhibition.getId(), exhibition.getType().name());
		}
	}

	private static String name(ExhibitionRegion region) {
		return region == null ? null : region.name();
	}

	private static String name(ExhibitionCategory category) {
		return category == null ? null : category.name();
	}

	private static String name(ExhibitionFormat format) {
		return format == null ? null : format.name();
	}
}
