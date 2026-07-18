package modi.backend.application.exhibition.serving;

import java.time.LocalDate;
import java.util.List;

import modi.backend.domain.exhibition.catalog.Exhibition;
import modi.backend.domain.exhibition.catalog.ExhibitionCategory;
import modi.backend.domain.exhibition.catalog.ExhibitionDetail;
import modi.backend.domain.exhibition.catalog.ExhibitionFormat;
import modi.backend.domain.exhibition.catalog.ExhibitionGenre;
import modi.backend.domain.exhibition.catalog.ExhibitionPlace;
import modi.backend.domain.exhibition.catalog.ExhibitionRegion;
import modi.backend.domain.exhibition.catalog.ExhibitionRegionGroup;
import modi.backend.domain.exhibition.hours.PlaceHours;

/**
 * 전시 유스케이스 출력 모음. (Facade는 Result까지만)
 * 장소(name/region/주소/gps)는 {@link ExhibitionPlace}(N:1), 상세(price/description/img)는 {@link ExhibitionDetail}(1:1),
 * 영업시간은 {@link PlaceHours}, 작가는 조인에서 조립해 Facade가 넘긴다 — 응답 필드명·타입은 이관 전과 동일하다(API 계약 불변).
 * 결측 필드는 원천 특성상 null로 내린다.
 */
public final class ExhibitionResult {

	private ExhibitionResult() {
	}

	/** 목록 한 페이지 결과 — 커서 페이지네이션 shape(content·nextCursor·hasNext·totalCount). */
	public record ListPage(List<ListItem> content, String nextCursor, boolean hasNext, long totalCount) {
	}

	/** 지역 필터 그룹(디자인 병합 칩) — code=그룹 식별자, regions=검색 region 파라미터로 펼칠 코드들. */
	public record RegionGroup(String code, String label, List<String> regions) {

		public static RegionGroup from(ExhibitionRegionGroup group) {
			return new RegionGroup(group.name(), group.label(),
				group.regions().stream().map(Enum::name).toList());
		}
	}

	/**
	 * 목록 항목(5.2 content[]). place·region은 이제 {@link ExhibitionPlace} 조인에서 온다. free는 상세 가격에서 파생해
	 * Facade가 주입한다(목록은 CATALOG만이라 artistSummary는 원천 미보유 → null). bookmarked는 Facade가 배치 조회해 주입.
	 */
	public record ListItem(Long exhibitionId, String type, String title, String posterUrl,
			LocalDate startDate, LocalDate endDate, String place, String region, String category,
			String artistSummary, Integer dDay, boolean free, boolean bookmarked) {

		public static ListItem from(Exhibition exhibition, ExhibitionPlace place, LocalDate today, boolean free,
				boolean bookmarked) {
			return new ListItem(exhibition.getId(), exhibition.getType().name(), exhibition.getTitle(),
					exhibition.getPosterUrl(), exhibition.getStartDate(), exhibition.getEndDate(),
					place == null ? null : place.getName(), place == null ? null : name(place.getRegion()),
					name(exhibition.getCategory()), null, exhibition.dDay(today), free, bookmarked);
		}
	}

	/**
	 * 전시 상세(5.3). place·주소·gps·operatingHours는 조인(장소·영업시간)에서, price·description·imgUrl은 상세 satellite에서,
	 * artists·artistSummary는 작가 조인에서 조립한다. keywords는 정준층(장르). bookmarked·recorded는 요청자 기준(비로그인 false).
	 */
	public record Detail(Long exhibitionId, String type, String title, String posterUrl,
			LocalDate startDate, LocalDate endDate, String place, String region, String category, String format,
			String description, String operatingHours, String price, List<String> artists, List<String> keywords,
			String serviceName, String detailUrl, Double gpsX, Double gpsY,
			String address, String imgUrl, String phone, long viewCount, String sigungu, String placeUrl,
			String artistSummary, boolean free, boolean bookmarked, boolean recorded) {

		public static Detail from(Exhibition exhibition, ExhibitionPlace place, ExhibitionDetail detail,
				PlaceHours placeHours, List<String> artistNames, ExhibitionGenre genre, boolean bookmarked,
				boolean recorded) {
			List<String> artists = artistNames == null ? List.of() : List.copyOf(artistNames);
			String artistSummary = artists.isEmpty() ? null : String.join(", ", artists);
			// 장르 키워드(분류기가 부여)가 있으면 keywords로 노출. 미분류면 빈 배열.
			List<String> keywords = genre == null || genre.getGenreKeyword() == null
					|| genre.getGenreKeyword().isBlank()
					? List.of() : List.of(genre.getGenreKeyword());
			String price = detail == null ? null : detail.getPrice();
			return new Detail(exhibition.getId(), exhibition.getType().name(), exhibition.getTitle(),
					exhibition.getPosterUrl(), exhibition.getStartDate(), exhibition.getEndDate(),
					place == null ? null : place.getName(), place == null ? null : name(place.getRegion()),
					name(exhibition.getCategory()), name(exhibition.getFormat()),
					detail == null ? null : detail.getDescription(),
					placeHours == null ? null : placeHours.getFormatted(), price,
					artists, keywords, exhibition.getServiceName(), exhibition.getDetailUrl(),
					place == null ? null : place.getGpsX(), place == null ? null : place.getGpsY(),
					place == null ? null : place.getAddress(), detail == null ? null : detail.getImgUrl(),
					place == null ? null : place.getPhone(), exhibition.getOurViewCount(),
					place == null ? null : place.getSigungu(), place == null ? null : place.getPlaceUrl(),
					artistSummary, Exhibition.isFree(price), bookmarked, recorded);
		}
	}

	/** 홈 배너 항목(E-10). 배너 이미지는 전시 포스터(posterUrl)를 사용한다. */
	public record Banner(Long exhibitionId, String title, String bannerImageUrl,
			LocalDate startDate, LocalDate endDate, String place) {

		public static Banner from(Exhibition exhibition, ExhibitionPlace place) {
			return new Banner(exhibition.getId(), exhibition.getTitle(), exhibition.getPosterUrl(),
					exhibition.getStartDate(), exhibition.getEndDate(), place == null ? null : place.getName());
		}
	}

	/** 개인 전시 등록 결과(3.3.3). */
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
