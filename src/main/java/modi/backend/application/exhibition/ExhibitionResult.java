package modi.backend.application.exhibition;

import java.time.LocalDate;
import java.util.List;

import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionCategory;
import modi.backend.domain.exhibition.ExhibitionFormat;
import modi.backend.domain.exhibition.ExhibitionRegion;
import modi.backend.domain.exhibition.ExhibitionRegionGroup;

/**
 * 전시 유스케이스 출력 모음. (Facade는 Result까지만)
 * 결측 필드(좌표·썸네일·설명·운영시간·가격 등)는 원천 특성상 null로 내린다.
 * 커서 페이지네이션 봉투 변환은 Interface(Controller)에서 한다.
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
	 * 목록 항목(03_전시.md 5.2 content[]). artistSummary·dDay·free는 도메인에서 파생하고,
	 * bookmarked는 Facade가 배치 조회한 관심 여부를 주입한다(비로그인 false).
	 */
	public record ListItem(Long exhibitionId, String type, String title, String posterUrl,
			LocalDate startDate, LocalDate endDate, String place, String region, String category,
			String artistSummary, Integer dDay, boolean free, boolean bookmarked) {

		public static ListItem from(Exhibition exhibition, LocalDate today, boolean bookmarked) {
			return new ListItem(exhibition.getId(), exhibition.getType().name(), exhibition.getTitle(),
					exhibition.getPosterUrl(), exhibition.getStartDate(), exhibition.getEndDate(),
					exhibition.getPlace(), name(exhibition.getRegion()), name(exhibition.getCategory()),
					exhibition.artistSummary(), exhibition.dDay(today), exhibition.isFree(), bookmarked);
		}
	}

	/**
	 * 전시 상세(03_전시.md 5.3). artists·keywords는 원천 API(한눈에보는문화정보)가 제공하지 않아
	 * 현재 항상 빈 배열이다 — 04_전시_구현.md 오픈 질문 참고.
	 * artistSummary·free는 도메인 파생, bookmarked·recorded는 Facade가 요청자 기준으로 주입(비로그인 false).
	 */
	public record Detail(Long exhibitionId, String type, String title, String posterUrl,
			LocalDate startDate, LocalDate endDate, String place, String region, String category, String format,
			String description, String operatingHours, String price, List<String> artists, List<String> keywords,
			String serviceName, String detailUrl, Double gpsX, Double gpsY,
			String address, String imgUrl, String phone, long viewCount, String sigungu, String placeUrl,
			String artistSummary, boolean free, boolean bookmarked, boolean recorded) {

		public static Detail from(Exhibition exhibition, boolean bookmarked, boolean recorded) {
			List<String> artists = exhibition.getArtist() == null || exhibition.getArtist().isBlank()
					? List.of() : List.of(exhibition.getArtist());
			// 장르 키워드(분류기가 부여)가 있으면 keywords로 노출. 미분류면 빈 배열.
			List<String> keywords = exhibition.getGenreKeyword() == null || exhibition.getGenreKeyword().isBlank()
					? List.of() : List.of(exhibition.getGenreKeyword());
			return new Detail(exhibition.getId(), exhibition.getType().name(), exhibition.getTitle(),
					exhibition.getPosterUrl(), exhibition.getStartDate(), exhibition.getEndDate(),
					exhibition.getPlace(), name(exhibition.getRegion()), name(exhibition.getCategory()),
					name(exhibition.getFormat()),
					exhibition.getDescription(), exhibition.getOperatingHours(), exhibition.getPrice(),
					artists, keywords, exhibition.getServiceName(), exhibition.getDetailUrl(),
					exhibition.getGpsX(), exhibition.getGpsY(),
					exhibition.getPlaceAddr(), exhibition.getImgUrl(), exhibition.getPhone(),
					exhibition.getOurViewCount(), exhibition.getSigungu(), exhibition.getPlaceUrl(),
					exhibition.artistSummary(), exhibition.isFree(), bookmarked, recorded);
		}
	}

	/** 홈 배너 항목(03_전시.md E-10). 배너 이미지는 전시 포스터(posterUrl)를 사용한다. */
	public record Banner(Long exhibitionId, String title, String bannerImageUrl,
			LocalDate startDate, LocalDate endDate, String place) {

		public static Banner from(Exhibition exhibition) {
			return new Banner(exhibition.getId(), exhibition.getTitle(), exhibition.getPosterUrl(),
					exhibition.getStartDate(), exhibition.getEndDate(), exhibition.getPlace());
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
