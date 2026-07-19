package modi.backend.ingestion.domain.draft;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import modi.backend.domain.exhibition.catalog.ExhibitionCategory;
import modi.backend.domain.exhibition.catalog.ExhibitionRegion;
import modi.backend.domain.exhibition.genre.GenreProvider;
import modi.backend.ingestion.domain.data.CatalogDetailData;
import modi.backend.ingestion.domain.data.CatalogExhibitionData;
import modi.backend.ingestion.domain.data.GenreResult;

/**
 * 전시 초기화 스테이징 — {@code exhibition_draft} 매핑. <b>초기화 in-flight 상태의 단독 보유자</b>다(ADR-10).
 *
 * <p>유효성 그라디언트(ADR-02)의 "정준층: 불완전 허용"을 구현한다: 벤더(뭐든 허용) → <b>draft(불완전 허용)</b> →
 * 도메인(완전). 목록분·상세분·장르분이 스텝별로 도착해 nullable 컬럼에 쌓이고, <b>필수 스텝이 전부 해소된 순간에만</b>
 * 진짜 {@code Exhibition}으로 승격된다 — 도메인은 완성분만 받는다(null-free 완성).
 *
 * <p>승격 게이트(사용자 확정): 목록(스테이징 시 확정) + 상세 스텝 <b>해소</b>(값 도착 또는 원천 무상세 확인 —
 * {@code detail_resolved_at}) + 장르 필수({@code genre_keyword}). 영업시간은 선택(승격 후 장소 대상).
 *
 * <p>모든 전이는 이 Entity의 메서드 안에서만 일어나고, 동시 완주 경합은 {@link Version 낙관락} +
 * {@code exhibitions.external_id} UK가 멱등을 보장한다. 스텝 반영 메서드는 재전달(at-least-once)에 안전하도록
 * 멱등이다(이미 반영된 스텝은 no-op).
 *
 * <p>재생성될 수 있는 파이프라인 테이블이라 {@code BaseEntity}(soft delete·감사)를 상속하지 않고
 * {@code created_at/updated_at}만 자체 관리한다(아웃박스·벤더 테이블과 같은 규율).
 */
@Entity
@Table(name = "exhibition_draft")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExhibitionDraft {

	/** last_error가 무한정 커지지 않게 저장 전 자르는 상한(원인 식별엔 충분하다). */
	private static final int MAX_ERROR_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 원천키 — 전시 승격 시 {@code exhibitions.external_id}가 된다. UK(중복 스테이징 방지). */
	@Column(name = "external_id", nullable = false, length = 255)
	private String externalId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private DraftStatus status;

	// ── 목록분(스테이징 시 확정 — 재sync가 갱신) ──────────────────────────────────

	@Column(name = "title", nullable = false, length = 500)
	private String title;

	/** 전시장 이름(원천 place) — 승격 시 전시장 resolve-or-create의 자연키 재료(ADR-07). */
	@Column(name = "place_name", length = 500)
	private String placeName;

	@Enumerated(EnumType.STRING)
	@Column(name = "region", length = 30)
	private ExhibitionRegion region;

	@Column(name = "sigungu", length = 100)
	private String sigungu;

	@Column(name = "gps_x")
	private Double gpsX;

	@Column(name = "gps_y")
	private Double gpsY;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", length = 30)
	private ExhibitionCategory category;

	@Column(name = "poster_url", length = 1000)
	private String posterUrl;

	@Column(name = "detail_url", length = 1000)
	private String detailUrl;

	@Column(name = "service_name", length = 255)
	private String serviceName;

	/** 원천 분야명(realmnm) — 장르 분류 입력 재료(전시 코어엔 저장되지 않는 값이라 draft가 들고 있는다). */
	@Column(name = "realm_name", length = 255)
	private String realmName;

	// ── 상세분(FETCH_DETAIL 해소 시) ─────────────────────────────────────────────

	@Column(name = "price", length = 500)
	private String price;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "img_url", length = 1000)
	private String imgUrl;

	@Column(name = "place_addr", length = 500)
	private String placeAddr;

	@Column(name = "place_phone", length = 100)
	private String placePhone;

	@Column(name = "place_url", length = 1000)
	private String placeUrl;

	/**
	 * 상세 스텝 <b>해소</b> 시각 — 값 도착과 "원천에 상세 없음 확인"을 구분하지 않는다(둘 다 스텝 완료).
	 * 무상세 전시를 영구 미승격에 가두지 않기 위한 게이트 정의다(기존 markDetailChecked 의미 보존).
	 */
	@Column(name = "detail_resolved_at")
	private LocalDateTime detailResolvedAt;

	// ── 장르분(CLASSIFY_GENRE 해소 시) ───────────────────────────────────────────

	@Column(name = "genre_keyword", length = 50)
	private String genreKeyword;

	@Enumerated(EnumType.STRING)
	@Column(name = "genre_provider", length = 20)
	private GenreProvider genreProvider;

	@Column(name = "genre_model", length = 100)
	private String genreModel;

	@Column(name = "genre_classified_at")
	private LocalDateTime genreClassifiedAt;

	// ── 종료·추적 ────────────────────────────────────────────────────────────────

	/** 승격으로 생성된 전시 id(COMPLETED에서만). 논리 참조 — draft는 파이프라인 소유물이라 FK를 걸지 않는다. */
	@Column(name = "promoted_exhibition_id")
	private Long promotedExhibitionId;

	/** 마지막 실패 원인(FAILED 가시화). */
	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	/** 낙관락 — 상세·장르 스텝이 동시에 완주해 둘 다 승격을 시도하는 경합에서 한쪽만 이긴다. */
	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	/** 종료(COMPLETED·FAILED) 시각. */
	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private ExhibitionDraft(CatalogExhibitionData data) {
		this.externalId = data.externalId();
		this.status = DraftStatus.PENDING;
		copyListFields(data);
	}

	/** 목록 1건을 스테이징한다 — 초기화의 진입점. 필수 스텝 메시지 enqueue는 같은 트랜잭션에서 파사드가 조율한다. */
	public static ExhibitionDraft stage(CatalogExhibitionData data) {
		if (data == null || !data.isPersistable()) {
			throw new IllegalArgumentException("externalId·title 없는 원천 데이터는 스테이징할 수 없다");
		}
		return new ExhibitionDraft(data);
	}

	/** 재sync가 같은 원천을 다시 만났다 — 목록분을 원천 최신값으로 갱신한다(종료 draft는 손대지 않는 것이 호출부 규약). */
	public void refreshFromList(CatalogExhibitionData data) {
		if (this.status.isTerminal()) {
			return; // 종료 draft는 불변 — 재스테이징이 필요하면 수동 개입(운영 결정)으로만.
		}
		copyListFields(data);
	}

	/** 상세 값이 도착했다(FETCH_DETAIL 해소). 이미 해소된 스텝이면 no-op(재전달 멱등). */
	public void applyDetail(CatalogDetailData detail, LocalDateTime now) {
		if (this.status.isTerminal() || this.detailResolvedAt != null) {
			return;
		}
		this.price = detail.price();
		this.description = detail.description();
		this.imgUrl = detail.imgUrl();
		this.placeAddr = detail.placeAddr();
		this.placePhone = detail.phone();
		this.placeUrl = detail.placeUrl();
		resolveDetail(now);
	}

	/** 원천에 상세가 없음을 확인했다 — 값 없이 스텝만 해소한다(무상세 전시의 영구 미승격 방지). */
	public void markDetailAbsent(LocalDateTime now) {
		if (this.status.isTerminal() || this.detailResolvedAt != null) {
			return;
		}
		resolveDetail(now);
	}

	/** 장르가 분류됐다(CLASSIFY_GENRE 해소). 이미 해소된 스텝이면 no-op(재전달 멱등). */
	public void applyGenre(GenreResult result, LocalDateTime now) {
		if (this.status.isTerminal() || this.genreKeyword != null) {
			return;
		}
		this.genreKeyword = result.genreKeyword();
		this.genreProvider = result.provider();
		this.genreModel = result.model();
		this.genreClassifiedAt = now;
		markEnriching();
	}

	/**
	 * 승격 게이트(사용자 확정) — 목록 코어(제목·전시장) + 상세 스텝 해소 + 장르 필수. 영업시간은 선택이라 보지 않는다.
	 */
	public boolean isReadyForPromotion() {
		return !this.status.isTerminal()
				&& this.title != null && !this.title.isBlank()
				&& this.placeName != null && !this.placeName.isBlank()
				&& this.detailResolvedAt != null
				&& this.genreKeyword != null;
	}

	/** 승격 완료 — 생성된 전시 id를 남기고 종료한다. 게이트 미충족 승격은 프로그래밍 오류다(조용히 넘기지 않는다). */
	public void complete(Long exhibitionId, LocalDateTime now) {
		if (!isReadyForPromotion()) {
			throw new IllegalStateException("승격 게이트 미충족 draft를 종료할 수 없다: " + this.externalId);
		}
		this.status = DraftStatus.COMPLETED;
		this.promotedExhibitionId = exhibitionId;
		this.lastError = null;
		this.completedAt = now;
	}

	/** 필수 스텝의 영구 실패 — 승격 불가로 종료한다(운영자 가시화). 이미 종료면 no-op. */
	public void fail(String error, LocalDateTime now) {
		if (this.status.isTerminal()) {
			return;
		}
		this.status = DraftStatus.FAILED;
		this.lastError = truncate(error);
		this.completedAt = now;
	}

	/** 상세 스텝이 아직 해소되지 않은 보강 대상인가(핸들러의 draft 경로 판정). */
	public boolean needsDetail() {
		return !this.status.isTerminal() && this.detailResolvedAt == null;
	}

	/** 장르 스텝이 아직 해소되지 않은 보강 대상인가. */
	public boolean needsGenre() {
		return !this.status.isTerminal() && this.genreKeyword == null;
	}

	private void copyListFields(CatalogExhibitionData data) {
		this.title = data.title();
		this.placeName = data.place();
		this.region = data.region();
		this.sigungu = data.sigungu();
		this.gpsX = data.gpsX();
		this.gpsY = data.gpsY();
		this.startDate = data.startDate();
		this.endDate = data.endDate();
		this.category = data.category();
		this.posterUrl = data.posterUrl();
		this.detailUrl = data.detailUrl();
		this.serviceName = data.serviceName();
		this.realmName = data.realmName();
	}

	private void resolveDetail(LocalDateTime now) {
		this.detailResolvedAt = now;
		markEnriching();
	}

	private void markEnriching() {
		if (this.status == DraftStatus.PENDING) {
			this.status = DraftStatus.ENRICHING;
		}
	}

	private static String truncate(String error) {
		if (error == null) {
			return null;
		}
		return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
	}

	@PrePersist
	private void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	private void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
