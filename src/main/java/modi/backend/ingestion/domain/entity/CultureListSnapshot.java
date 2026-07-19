package modi.backend.ingestion.domain.entity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import modi.backend.ingestion.domain.data.CatalogVendorItem;

/**
 * 한눈에보는문화정보 목록(realm2) 응답 스냅샷(벤더층) — {@code culture_list_snapshot} 매핑.
 *
 * <p><b>행 = 목록 응답 중 전시 1건의 스냅샷</b>이다(페이지 단위 ❌). UK({@code external_id})라 재수집이 no-op가
 * 되어 크기가 원천(280건 수준)에 수렴한다.
 *
 * <p><b>응답 구조 필드 적재(ADR-13, ADR-01 폐기)</b>: raw payload 문자열 대신 realm2 응답 아이템의 필드를
 * <b>원문 verbatim</b> 컬럼으로 적재한다 — 도메인 변환(디코드·타입 정제) 이전 값이라 "원천이 뭐라고 했나"의
 * 증거 역할은 유지되고, 도메인은 필요에 맞게 가져다 매핑·가공한다. 실측으로 응답 구조가 확정돼 있어(제공률
 * 분석 문서) 이 분해가 무손실이다. 대가: 원천이 신규 필드를 추가하면 {@code CatalogVendorItem}에 선언을
 * 더해야 한다(수용 — ADR-13).
 *
 * <p>{@code payload_hash}로 원천이 값을 정정했는지 <b>행 단위</b>로 감지한다(필드 정준 문자열의 SHA-256 —
 * 과거 payload 해시의 승계). {@code last_seen_at}은 이번 동기화에도 원천에 있었는지 — 사라진 항목 판별용.
 *
 * <p>{@code exhibitions}와는 ID·키 참조도 두지 않는다 — 벤더층은 도메인이 적재하지 않은 항목(기간 불량 스킵 등)도
 * 기록한다. 감사 컬럼 대신 {@code first_seen_at}·{@code last_seen_at}이 도메인 언어로 그 역할을 한다.
 */
@Entity
@Table(name = "culture_list_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CultureListSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, length = 100)
	private String externalId;

	// ── realm2 응답 아이템 필드(원문 verbatim — 타입 정제는 도메인 몫) ──
	@Column(name = "title", length = 500)
	private String title;

	@Column(name = "start_date", length = 20)
	private String startDate;

	@Column(name = "end_date", length = 20)
	private String endDate;

	@Column(name = "place", length = 300)
	private String place;

	@Column(name = "realm_name", length = 100)
	private String realmName;

	@Column(name = "area", length = 100)
	private String area;

	@Column(name = "sigungu", length = 100)
	private String sigungu;

	@Column(name = "thumbnail", length = 1000)
	private String thumbnail;

	@Column(name = "gps_x", length = 50)
	private String gpsX;

	@Column(name = "gps_y", length = 50)
	private String gpsY;

	@Column(name = "service_name", length = 200)
	private String serviceName;

	@Column(name = "place_seq", length = 50)
	private String placeSeq;

	/** 필드 정준 문자열의 SHA-256 hex(64자) — 원천 정정 감지용(과거 payload 해시의 승계, 컬럼명 유지). */
	@Column(name = "payload_hash", length = 64)
	private String payloadHash;

	@Column(name = "first_seen_at")
	private LocalDateTime firstSeenAt;

	@Column(name = "last_seen_at")
	private LocalDateTime lastSeenAt;

	private CultureListSnapshot(String externalId, CatalogVendorItem item, LocalDateTime now) {
		this.externalId = externalId;
		copyFields(item);
		this.payloadHash = hash(item);
		this.firstSeenAt = now;
		this.lastSeenAt = now;
	}

	/** 원천에서 처음 본 아이템. */
	public static CultureListSnapshot first(String externalId, CatalogVendorItem item, LocalDateTime now) {
		return new CultureListSnapshot(externalId, item, now);
	}

	/**
	 * 이번 동기화에도 원천에 있었다 — {@code last_seen_at}은 값이 그대로여도 항상 갱신한다("아직 살아 있다"는 사실 자체가 정보다).
	 * 필드는 <b>해시가 달라졌을 때만</b> 덮는다 — 원천이 값을 정정한 경우다(같은 값으로 매일 덮으면 "언제 바뀌었나"를 잃는다).
	 * 레거시 행(V39 이전 — 해시 null 리셋)은 첫 재동기화에서 "변경됨"으로 판정돼 필드가 자동 채워진다.
	 */
	public void seenAgain(CatalogVendorItem item, LocalDateTime now) {
		this.lastSeenAt = now;
		String incoming = hash(item);
		if (incoming != null && !incoming.equals(this.payloadHash)) {
			copyFields(item);
			this.payloadHash = incoming;
		}
	}

	/** 원천이 이 아이템의 값을 정정했는가 — 저장된 해시와 비교한다. */
	public boolean isChangedFrom(CatalogVendorItem item) {
		String incoming = hash(item);
		return incoming != null && !incoming.equals(this.payloadHash);
	}

	private void copyFields(CatalogVendorItem item) {
		if (item == null) {
			return;
		}
		this.title = item.title();
		this.startDate = item.startDate();
		this.endDate = item.endDate();
		this.place = item.place();
		this.realmName = item.realmName();
		this.area = item.area();
		this.sigungu = item.sigungu();
		this.thumbnail = item.thumbnail();
		this.gpsX = item.gpsX();
		this.gpsY = item.gpsY();
		this.serviceName = item.serviceName();
		this.placeSeq = item.placeSeq();
	}

	/**
	 * 필드 정준 문자열의 SHA-256 hex. item이 null이면 null(해시할 원문이 없다 — "변경 없음"과 구분된다).
	 * SHA-256은 모든 JRE가 반드시 제공하므로 미지원은 발생할 수 없다.
	 */
	private static String hash(CatalogVendorItem item) {
		if (item == null) {
			return null;
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(item.canonical().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 미지원 JRE", e);
		}
	}
}
