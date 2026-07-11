package modi.backend.domain.exhibition;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.hibernate.annotations.DynamicUpdate;

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
// 변경된 컬럼만 UPDATE — 보강(장르·상세)과 조회수 증가 등이 같은 행을 짧은 시간차로
// 갱신할 때 서로의 전체-컬럼 UPDATE가 상대 필드를 덮어쓰는 lost update를 막는다(@Version 미도입 환경 방어).
@DynamicUpdate
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

	/**
	 * 장르 키워드(마스터 중 1개, {@link GenreKeyword}). {@link GenreClassifier}(랜덤/AI)가 부여한다 —
	 * CUSTOM은 등록 시, CATALOG는 초기화 백필({@code applyGenre}) 시 채운다. 미분류(백필 전/폴백 전)는 null. 상세의 keywords로 노출.
	 */
	@Column(name = "genre_keyword", length = 50)
	private String genreKeyword;

	private Exhibition(ExhibitionType type, String externalId, Long ownerId, String title, String place,
			LocalDate startDate, LocalDate endDate, ExhibitionRegion region, ExhibitionCategory category,
			ExhibitionFormat format, String artist, String posterUrl, String description, String operatingHours,
			String price, String detailUrl, String serviceName, Double gpsX, Double gpsY, String sigungu,
			String realmName, String areaText, String genreKeyword) {
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
		this.genreKeyword = genreKeyword;
		validatePeriod();
		validateSoloArtist();
	}

	/**
	 * 사용자 개인 전시(CUSTOM) 등록. 제목 필수, 기간 {@code RULE: 전시 기간}·{@code RULE: 개인전 작가} 검증.
	 * format·artist는 선택이나 {@code format=SOLO}면 artist 필수.
	 * {@code genreKeyword}는 앱 레이어가 {@link GenreClassifier}로 산출해 넘긴다(랜덤/AI).
	 */
	public static Exhibition createCustom(Long ownerId, String title, String place, LocalDate startDate,
			LocalDate endDate, ExhibitionRegion region, ExhibitionCategory category, ExhibitionFormat format,
			String artist, String posterUrl, String genreKeyword) {
		return new Exhibition(ExhibitionType.CUSTOM, null, ownerId, title, place, startDate, endDate,
				region, category, format, artist, posterUrl, null, null, null, null, null, null, null, null, null,
				null, genreKeyword);
	}

	/** 외부 API 수집 전시(CATALOG) 생성. {@code externalId}는 동기화 upsert 기준키. */
	public static Exhibition createCatalog(String externalId, String title, String place, LocalDate startDate,
			LocalDate endDate, ExhibitionRegion region, ExhibitionCategory category, String posterUrl,
			String description, String operatingHours, String price, String detailUrl, String serviceName,
			Double gpsX, Double gpsY, String sigungu, String realmName, String areaText) {
		return new Exhibition(ExhibitionType.CATALOG, externalId, null, title, place, startDate, endDate,
				region, category, null, null, posterUrl, description, operatingHours, price, detailUrl, serviceName,
				gpsX, gpsY, sigungu, realmName, areaText, null);
	}

	/**
	 * 분류기(랜덤/AI)가 산출한 장르 키워드를 부여한다(CATALOG 초기화 백필·재분류용). 공백/null은 무시해 기존 값을 지키지 않는다
	 * — 초기화는 부가 처리라 유효한 값일 때만 반영한다.
	 */
	public void applyGenre(String genreKeyword) {
		if (genreKeyword != null && !genreKeyword.isBlank()) {
			this.genreKeyword = genreKeyword.trim();
		}
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

	/**
	 * 상세 확인 완료 표기(값은 채우지 않음) — 원천 상세2에 항목이 없어(상세 미보유) 채울 게 없을 때 호출한다.
	 * detailSyncedAt만 기록해 상세 백필의 재조회 대상(detailSyncedAt IS NULL)에서 빠지게 한다
	 * (매 보강 주기마다 상세 없는 행에 외부 호출을 반복하지 않도록). 일시 실패는 예외로 처리되어 이 경로를 타지 않는다.
	 */
	public void markDetailChecked() {
		if (this.detailSyncedAt == null) {
			this.detailSyncedAt = LocalDateTime.now();
		}
	}

	/** 우리 앱 내 조회 1회 발생 시 호출(인기순 정렬용 카운터). */
	public void increaseView() {
		this.ourViewCount += 1;
	}

	public boolean isCatalog() {
		return type == ExhibitionType.CATALOG;
	}

	/**
	 * 무료 여부(C-6 규칙) — 가격 텍스트가 "무료"를 포함하거나, 표기된 금액이 0뿐이면 무료.
	 * null/공백(가격 미상)은 무료로 보지 않는다. 목록 {@code free} 필드와 {@code section=free} 필터가 공유하는 단일 규칙.
	 */
	public static boolean isFree(String price) {
		if (price == null || price.isBlank()) {
			return false;
		}
		if (price.contains("무료")) {
			return true;
		}
		String digits = price.replaceAll("[^0-9]", "");
		return digits.matches("0+");
	}

	public boolean isFree() {
		return isFree(this.price);
	}

	/**
	 * 종료 D-데이(오늘로부터 종료일까지 남은 일수). 종료일이 없거나 이미 종료됐으면 null.
	 * (오늘 == 종료일이면 D-0)
	 */
	public Integer dDay(LocalDate today) {
		if (endDate == null || endDate.isBefore(today)) {
			return null;
		}
		return (int) java.time.temporal.ChronoUnit.DAYS.between(today, endDate);
	}

	/** 작가 요약(목록·상세의 artistSummary). CUSTOM은 등록 작가명, CATALOG는 원천 미보유라 null. */
	public String artistSummary() {
		if (isCatalog() || artist == null || artist.isBlank()) {
			return null;
		}
		return artist;
	}

	/** 요청자가 이 전시를 조회할 수 있는가. CATALOG는 공개, CUSTOM은 등록자 본인만. */
	public boolean isAccessibleBy(Long requesterId) {
		return isCatalog() || (requesterId != null && requesterId.equals(ownerId));
	}

	/**
	 * 요청자가 직접 등록한 개인(CUSTOM) 전시인가 — 기록 삭제 시 동반 삭제 가능 여부 판단용.
	 * 공용 CATALOG나 타인의 CUSTOM은 삭제 대상이 아니다.
	 */
	public boolean isCustomOwnedBy(Long requesterId) {
		return type == ExhibitionType.CUSTOM && requesterId != null && requesterId.equals(ownerId);
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

	/** {@code RULE: 개인전 작가} — format=SOLO(개인전)면 작가명이 필요하다. */
	private void validateSoloArtist() {
		if (format == ExhibitionFormat.SOLO && (artist == null || artist.isBlank())) {
			throw new CoreException(ErrorType.INVALID_INPUT, "개인전은 작가명이 필요합니다");
		}
	}

	@Override
	protected void guard() {
		validatePeriod();
	}
}
