package modi.backend.domain.venue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import modi.backend.domain.exhibition.catalog.ExhibitionRegion;
import modi.backend.support.entity.BaseEntity;

/**
 * 전시관(venue) 마스터. 개인 전시 직접 등록 시 전시관명 자동완성(GET /venues)과 venueId 선택에 쓰인다.
 * 지역은 전시와 같은 {@link ExhibitionRegion} 축을 재사용한다.
 */
@Entity
@Table(name = "venues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue extends BaseEntity {

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 500)
	private String address;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ExhibitionRegion region;

	private Venue(String name, String address, ExhibitionRegion region) {
		this.name = name;
		this.address = address;
		this.region = region;
	}

	public static Venue create(String name, String address, ExhibitionRegion region) {
		return new Venue(name, address, region);
	}
}
