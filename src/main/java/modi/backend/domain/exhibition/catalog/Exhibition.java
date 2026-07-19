package modi.backend.domain.exhibition.catalog;

import modi.backend.domain.exhibition.hours.PlaceHours;

import java.time.LocalDate;
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
 * 전시(애그리거트 루트). 두 출처를 하나의 테이블로 다룬다(CUSTOM 독립 엔티티 모델링).
 * <ul>
 *   <li>CATALOG: 외부 전시 API에서 동기화. {@code externalId}(원천 seq)로 upsert, {@code ownerId=null} → 전체 공개.</li>
 *   <li>CUSTOM: 사용자가 직접 등록. {@code ownerId}=등록자 → 등록자 본인에게만 노출.</li>
 * </ul>
 *
 * <p><b>코어는 생성 시점에 완결된다</b>(ADR-02·03): 전 컬럼이 목록(list) 소스에서 오거나 등록 입력이라 생성과 동시에 확정된다.
 * 지연 도착 정보는 집합체 밖으로 나갔다 — 장소(name/region/gps/주소)는 {@link ExhibitionPlace}(N:1), 상세(price/description/
 * img)는 {@link ExhibitionDetail}(1:1), 영업시간은 {@link PlaceHours}, 장르는 {@link ExhibitionGenre}, 작가는
 * {@link Artist}+{@link ExhibitionArtist}(N:M). "부재"는 코어의 null이 아니라 <b>연관의 부재</b>로 표현한다.
 *
 * <p>의도된 판별 null은 둘뿐이다: {@code ownerId}(CUSTOM만), {@code externalId}·{@code detailUrl}·{@code serviceName}
 * (CATALOG만 — 목록 소스라 생성 시점 확정, ADR-02 "부재는 타입으로"). 상태 변경은 이 Entity 메서드 안에서만 한다.
 */
@Entity
@Table(name = "exhibitions")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exhibition extends BaseEntity {

	private static final int TITLE_MAX_LENGTH = 100;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ExhibitionType type;

	/** 외부 전시 API의 원천 식별자(seq). CATALOG 동기화 upsert 기준키. CUSTOM은 null(의도된 판별 null). */
	@Column(name = "external_id", length = 100)
	private String externalId;

	/** CUSTOM 전시의 등록자. CATALOG는 null(공개, 의도된 판별 null). */
	@Column(name = "owner_id")
	private Long ownerId;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	/** 전시장(N:1) 참조 — 경계 넘는 FK 아님, ID 논리 참조. 생성 시점 확정이라 NOT NULL(ADR-05·06). */
	@Column(name = "exhibition_place_id", nullable = false)
	private Long exhibitionPlaceId;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ExhibitionCategory category;

	/** 전시 형태(개인전/단체전/기획전/아트페어). CUSTOM 등록 시 선택. CATALOG는 null(원천 미보유). */
	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ExhibitionFormat format;

	/** 포스터 이미지 URL(목록 thumbnail 소스 — 코어 잔류, 설계 §1 교정). 없으면 null. */
	@Column(name = "poster_url", length = 2048)
	private String posterUrl;

	/** 원문 상세 페이지 링크. CATALOG 목록 소스, CUSTOM은 null. */
	@Column(name = "detail_url", length = 2048)
	private String detailUrl;

	/** 제공(연계) 기관명. CATALOG 목록 소스, CUSTOM은 null. */
	@Column(name = "service_name", length = 200)
	private String serviceName;

	/** 우리 앱 내 조회수(인기순 정렬용). 외부 API의 조회수와 별개. */
	@Column(name = "our_view_count", nullable = false)
	private long ourViewCount = 0;

	private Exhibition(ExhibitionType type, String externalId, Long ownerId, String title, Long exhibitionPlaceId,
			LocalDate startDate, LocalDate endDate, ExhibitionCategory category, ExhibitionFormat format,
			String artist, String posterUrl, String detailUrl, String serviceName) {
		this.type = type;
		this.externalId = externalId;
		this.ownerId = ownerId;
		this.title = requireTitle(title);
		this.exhibitionPlaceId = exhibitionPlaceId;
		this.startDate = startDate;
		this.endDate = endDate;
		this.category = category;
		this.format = format;
		this.posterUrl = posterUrl;
		this.detailUrl = detailUrl;
		this.serviceName = serviceName;
		validatePeriod();
		validateSoloArtist(format, artist);
	}

	/**
	 * 사용자 개인 전시(CUSTOM) 등록. 제목 필수, 기간·개인전 작가 검증. {@code exhibitionPlaceId}는 Facade가 전시장을
	 * resolve-or-create(정규화 이름)해 넘긴다. {@code artist}는 SOLO 검증에만 쓰고 코어에 저장하지 않는다 —
	 * 작가는 {@link Artist}+{@link ExhibitionArtist}에 별도 저장한다(Facade가 조율).
	 */
	public static Exhibition createCustom(Long ownerId, String title, Long exhibitionPlaceId, LocalDate startDate,
			LocalDate endDate, ExhibitionCategory category, ExhibitionFormat format, String artist, String posterUrl) {
		return new Exhibition(ExhibitionType.CUSTOM, null, ownerId, title, exhibitionPlaceId, startDate, endDate,
				category, format, artist, posterUrl, null, null);
	}

	/** 외부 API 수집 전시(CATALOG) 생성. {@code externalId}는 동기화 upsert 기준키. 상세(price 등)는 별도 satellite로 지연 채움. */
	public static Exhibition createCatalog(String externalId, String title, Long exhibitionPlaceId, LocalDate startDate,
			LocalDate endDate, ExhibitionCategory category, String posterUrl, String detailUrl, String serviceName) {
		return new Exhibition(ExhibitionType.CATALOG, externalId, null, title, exhibitionPlaceId, startDate, endDate,
				category, null, null, posterUrl, detailUrl, serviceName);
	}

	/** 관리자 수정 — 전시장 재지정(place 이름 변경 시 Facade가 새 전시장을 resolve-or-create해 넘긴다). */
	public void reassignPlace(Long exhibitionPlaceId) {
		this.exhibitionPlaceId = exhibitionPlaceId;
	}

	/**
	 * 관리자 수정 — 제목이 실제로 바뀌면 변경 이력을 돌려준다(멱등). null 인자는 "건드리지 않음".
	 * (place는 전시장, price·description은 상세로 분리돼 각 엔티티가 자기 변경을 판단한다.)
	 */
	public Optional<FieldChange> applyTitleEdit(String title) {
		if (title == null || java.util.Objects.equals(this.title, title)) {
			return Optional.empty();
		}
		FieldChange change = new FieldChange("title", this.title, title);
		this.title = title;
		return Optional.of(change);
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
	 * null/공백(가격 미상)은 무료로 보지 않는다. 가격은 상세({@link ExhibitionDetail})에 있어 호출부가 값을 넘긴다.
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

	/**
	 * 종료 D-데이(오늘로부터 종료일까지 남은 일수). 종료일이 없거나 이미 종료됐으면 null. (오늘 == 종료일이면 D-0)
	 */
	public Integer dDay(LocalDate today) {
		if (endDate == null || endDate.isBefore(today)) {
			return null;
		}
		return (int) java.time.temporal.ChronoUnit.DAYS.between(today, endDate);
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
	private static void validateSoloArtist(ExhibitionFormat format, String artist) {
		if (format == ExhibitionFormat.SOLO && (artist == null || artist.isBlank())) {
			throw new CoreException(ErrorType.INVALID_INPUT, "개인전은 작가명이 필요합니다");
		}
	}

	@Override
	protected void guard() {
		validatePeriod();
	}
}
