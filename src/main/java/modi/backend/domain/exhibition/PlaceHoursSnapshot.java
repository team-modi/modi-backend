package modi.backend.domain.exhibition;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구글 Places(New) 영업시간 응답 원본(장소당 1행) — {@code google_place_hours} 매핑.
 * <p>
 * 영업시간 보강 실행마다 <b>전체 truncate 후 재적재</b>되는 per-run 스테이징이다(영속 캐시 아님).
 * 사용자에게 노출하는 표시값은 전시 행({@code exhibitions.operating_hours})에 파생 저장되고,
 * 이 테이블은 배치 내에서 "구글이 뭘 줬는지"의 원본 근거로만 남긴다. 그래서 {@link modi.backend.support.entity.BaseEntity}를
 * 상속하지 않고 최소 컬럼만 둔다(soft delete·감사 컬럼 불필요).
 */
@Entity
@Table(name = "google_place_hours")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceHoursSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "place_id", length = 256)
	private String placeId;

	@Column(name = "display_name", length = 200)
	private String displayName;

	@Column(name = "formatted_address", length = 500)
	private String formattedAddress;

	/** 조회에 사용한 원본 place_addr(전시가 어떤 주소로 이 장소에 매칭됐는지 추적용). */
	@Column(name = "queried_addr", length = 500)
	private String queriedAddr;

	@Column(name = "regular_opening_hours_json", columnDefinition = "text")
	private String regularOpeningHoursJson;

	/** 출처({@code GOOGLE} | {@code MOCK}). mock으로 채워진 행을 실호출과 구분한다. */
	@Column(length = 20)
	private String source;

	@Column(name = "fetched_at")
	private LocalDateTime fetchedAt;

	private PlaceHoursSnapshot(String placeId, String displayName, String formattedAddress, String queriedAddr,
			String regularOpeningHoursJson, String source, LocalDateTime fetchedAt) {
		this.placeId = placeId;
		this.displayName = displayName;
		this.formattedAddress = formattedAddress;
		this.queriedAddr = queriedAddr;
		this.regularOpeningHoursJson = regularOpeningHoursJson;
		this.source = source;
		this.fetchedAt = fetchedAt;
	}

	/** 조회 결과 원본을 스테이징 행으로 만든다. */
	public static PlaceHoursSnapshot of(PlaceHoursData data, String queriedAddr, LocalDateTime fetchedAt) {
		return new PlaceHoursSnapshot(data.placeId(), data.displayName(), data.formattedAddress(), queriedAddr,
				data.rawJson(), data.source(), fetchedAt);
	}
}
