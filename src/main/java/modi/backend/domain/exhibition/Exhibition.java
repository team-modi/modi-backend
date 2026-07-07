package modi.backend.domain.exhibition;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import modi.backend.support.entity.BaseEntity;
import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/**
 * 전시(애그리거트 루트). 두 출처를 하나의 테이블로 다룬다(03_전시.md 결정사항 — CUSTOM 독립 엔티티 모델링).
 * <ul>
 *   <li>CATALOG: 외부 전시 API에서 동기화. {@code externalId}(원천 seq)로 upsert, {@code ownerId=null} → 전체 공개.</li>
 *   <li>CUSTOM: 사용자가 직접 등록. {@code ownerId}=등록자 → 등록자 본인에게만 노출.</li>
 * </ul>
 * 상태 변경은 이 Entity 메서드 안에서만 한다(Facade는 load·조율·save). id·시각은 {@link BaseEntity}.
 */
@Entity
@Table(name = "exhibitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exhibition extends BaseEntity {

	private static final int TITLE_MAX_LENGTH = 100;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ExhibitionType type;

	/** 외부 전시 API의 원천 식별자(seq). CATALOG 동기화 upsert 기준키. CUSTOM은 null. */
	@Column(name = "external_id", length = 100)
	private String externalId;

	/** CUSTOM 전시의 등록자. CATALOG는 null(공개). */
	@Column(name = "owner_id")
	private Long ownerId;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	@Column(length = 200)
	private String place;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ExhibitionRegion region;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ExhibitionCategory category;

	/** 전시 형태(개인전/단체전/기획전/아트페어). CUSTOM 등록 시 선택. CATALOG는 null. */
	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ExhibitionFormat format;

	/** 참여 작가·주최명(예: "김선영"). CUSTOM 등록 시 입력. */
	@Column(length = 100)
	private String artist;

	@Column(name = "poster_url", length = 2048)
	private String posterUrl;

	@Column(columnDefinition = "text")
	private String description;

	/** 운영 시간(예: "10:00-18:00(월요일 휴관)"). 데이터 있을 때만. */
	@Column(name = "operating_hours", length = 500)
	private String operatingHours;

	/** 관람료(예: "성인 20,000원"). 데이터 있을 때만. */
	@Column(length = 500)
	private String price;

	/** 원문 상세 페이지 링크. */
	@Column(name = "detail_url", length = 2048)
	private String detailUrl;

	/** 제공(연계) 기관명. */
	@Column(name = "service_name", length = 200)
	private String serviceName;

	@Column(name = "gps_x")
	private Double gpsX;

	@Column(name = "gps_y")
	private Double gpsY;

	/** realmName 원문(예 "전시"). API 응답 필드를 누락 없이 영속(저장 정책). */
	@Column(name = "realm_name", length = 50)
	private String realmName;

	/** area 원문(region enum 파생 전 원본 보존). */
	@Column(name = "area_text", length = 50)
	private String areaText;

	@Column(length = 50)
	private String sigungu;

	/** 상세 지연수집 필드(상세 진입 시 채운다) — 장소 상세 주소. */
	@Column(name = "place_addr", length = 500)
	private String placeAddr;

	@Column(length = 100)
	private String phone;

	@Column(name = "place_url", length = 2048)
	private String placeUrl;

	@Column(name = "img_url", length = 2048)
	private String imgUrl;

	@Column(name = "place_seq", length = 100)
	private String placeSeq;

	/** 상세 필드가 마지막으로 동기화된 시각. null이면 아직 상세를 수집하지 않은 상태. */
	@Column(name = "detail_synced_at")
	private LocalDateTime detailSyncedAt;

	/** 우리 앱 내 조회수(인기순 정렬용). 외부 API의 조회수와 별개. */
	@Column(name = "our_view_count", nullable = false)
	private long ourViewCount = 0;

	private Exhibition(ExhibitionType type, String externalId, Long ownerId, String title, String place,
			LocalDate startDate, LocalDate endDate, ExhibitionRegion region, ExhibitionCategory category,
			ExhibitionFormat format, String artist, String posterUrl, String description, String operatingHours,
			String price, String detailUrl, String serviceName, Double gpsX, Double gpsY, String sigungu,
			String realmName, String areaText) {
		this.type = type;
		this.externalId = externalId;
		this.ownerId = ownerId;
		this.title = requireTitle(title);
		this.place = place;
		this.startDate = startDate;
		this.endDate = endDate;
		this.region = region;
		this.category = category;
		this.format = format;
		this.artist = artist;
		this.posterUrl = posterUrl;
		this.description = description;
		this.operatingHours = operatingHours;
		this.price = price;
		this.detailUrl = detailUrl;
		this.serviceName = serviceName;
		this.gpsX = gpsX;
		this.gpsY = gpsY;
		this.sigungu = sigungu;
		this.realmName = realmName;
		this.areaText = areaText;
		validatePeriod();
	}

	/** 사용자 개인 전시(CUSTOM) 등록. 제목 필수, 기간 {@code RULE: 전시 기간} 검증. format·artist는 선택. */
	public static Exhibition createCustom(Long ownerId, String title, String place, LocalDate startDate,
			LocalDate endDate, ExhibitionRegion region, ExhibitionCategory category, ExhibitionFormat format,
			String artist, String posterUrl) {
		return new Exhibition(ExhibitionType.CUSTOM, null, ownerId, title, place, startDate, endDate,
				region, category, format, artist, posterUrl, null, null, null, null, null, null, null, null, null,
				null);
	}

	/** 외부 API 수집 전시(CATALOG) 생성. {@code externalId}는 동기화 upsert 기준키. */
	public static Exhibition createCatalog(String externalId, String title, String place, LocalDate startDate,
			LocalDate endDate, ExhibitionRegion region, ExhibitionCategory category, String posterUrl,
			String description, String operatingHours, String price, String detailUrl, String serviceName,
			Double gpsX, Double gpsY, String sigungu, String realmName, String areaText) {
		return new Exhibition(ExhibitionType.CATALOG, externalId, null, title, place, startDate, endDate,
				region, category, null, null, posterUrl, description, operatingHours, price, detailUrl, serviceName,
				gpsX, gpsY, sigungu, realmName, areaText);
	}

	/**
	 * CATALOG 동기화 재적재 — 원천에서 다시 받은 값으로 카탈로그 필드를 갱신한다(같은 externalId 재수신 시).
	 * type·externalId·ownerId 등 정체성 필드는 바꾸지 않는다.
	 */
	public void refreshCatalog(String title, String place, LocalDate startDate, LocalDate endDate,
			ExhibitionRegion region, ExhibitionCategory category, String posterUrl, String description,
			String operatingHours, String price, String detailUrl, String serviceName, Double gpsX, Double gpsY,
			String sigungu, String realmName, String areaText) {
		this.title = requireTitle(title);
		this.place = place;
		this.startDate = startDate;
		this.endDate = endDate;
		this.region = region;
		this.category = category;
		this.posterUrl = posterUrl;
		this.description = description;
		this.operatingHours = operatingHours;
		this.price = price;
		this.detailUrl = detailUrl;
		this.serviceName = serviceName;
		this.gpsX = gpsX;
		this.gpsY = gpsY;
		this.sigungu = sigungu;
		this.realmName = realmName;
		this.areaText = areaText;
		validatePeriod();
	}

	/** 상세 지연수집(상세 진입 시 1회) — 목록엔 없던 필드를 채우고 동기화 시각을 기록한다. detailUrl은 값이 있을 때만 덮어쓴다. */
	public void applyDetail(CatalogDetailData d) {
		this.price = d.price();
		this.description = d.description();
		if (d.detailUrl() != null) {
			this.detailUrl = d.detailUrl();
		}
		this.phone = d.phone();
		this.imgUrl = d.imgUrl();
		this.placeUrl = d.placeUrl();
		this.placeAddr = d.placeAddr();
		this.placeSeq = d.placeSeq();
		this.detailSyncedAt = LocalDateTime.now();
	}

	public boolean isDetailSynced() {
		return detailSyncedAt != null;
	}

	/** 우리 앱 내 조회 1회 발생 시 호출(인기순 정렬용 카운터). */
	public void increaseView() {
		this.ourViewCount += 1;
	}

	public boolean isCatalog() {
		return type == ExhibitionType.CATALOG;
	}

	/** 요청자가 이 전시를 조회할 수 있는가. CATALOG는 공개, CUSTOM은 등록자 본인만. */
	public boolean isAccessibleBy(Long requesterId) {
		return isCatalog() || (requesterId != null && requesterId.equals(ownerId));
	}

	private String requireTitle(String value) {
		String trimmed = Optional.ofNullable(value).map(String::trim).orElse("");
		if (trimmed.isEmpty() || trimmed.length() > TITLE_MAX_LENGTH) {
			throw new CoreException(ErrorType.INVALID_INPUT, "전시 제목은 1~" + TITLE_MAX_LENGTH + "자여야 합니다: " + value);
		}
		return trimmed;
	}

	/** {@code RULE: 전시 기간} — startDate ≤ endDate. 둘 다 있을 때만 검증. */
	private void validatePeriod() {
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new CoreException(ErrorType.INVALID_INPUT, "종료일이 시작일보다 앞설 수 없습니다: " + startDate + " ~ " + endDate);
		}
	}

	@Override
	protected void guard() {
		validatePeriod();
	}
}
