package modi.backend.application.venue;

import java.util.List;

import modi.backend.domain.exhibition.catalog.ExhibitionRegion;
import modi.backend.domain.venue.Venue;

/**
 * 전시관 유스케이스 출력 모음. (Facade는 Result까지만)
 * region은 enum name 문자열(예: "SEOUL")로, 미입력이면 null로 내린다.
 */
public final class VenueResult {

	private VenueResult() {
	}

	/** 자동완성 검색 결과(상위 N개). */
	public record Search(List<Item> venues) {

		public static Search of(List<Venue> venues) {
			return new Search(venues.stream().map(Item::from).toList());
		}
	}

	/** 전시관 항목. address·region은 미입력 시 null. */
	public record Item(Long venueId, String name, String address, String region) {

		public static Item from(Venue venue) {
			return new Item(venue.getId(), venue.getName(), venue.getAddress(), regionCode(venue.getRegion()));
		}
	}

	private static String regionCode(ExhibitionRegion region) {
		return region == null ? null : region.name();
	}
}
