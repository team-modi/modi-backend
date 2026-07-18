package modi.backend.domain.exhibition.sync.entity;

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

/**
 * 한눈에보는문화정보 목록(realm2) 응답 원본(벤더층) — {@code culture_list_response} 매핑.
 *
 * <p><b>행 = 목록 응답 중 전시 1건의 조각</b>이다(페이지 단위 ❌). 페이지는 페이지네이션의 부산물일 뿐 의미 단위가 아니고,
 * 원본을 남기는 목적(재파싱·변경 감지)이 전부 아이템 키에서 작동하기 때문이다. UK({@code external_id})라 재수집이
 * no-op가 되어 크기가 원천(280건 수준)에 수렴한다 — 페이지 단위로 쌓으면 매일 3행씩 무한 누적된다.
 *
 * <p>{@code payload}는 원천 응답 아이템을 <b>매핑해 직렬화한 JSON</b>이다(도메인 변환 이전 값). 실측으로 원천의
 * 응답 구조가 확정돼 있어 이 직렬화가 <b>무손실</b>이기에 가능하다 — 목록 전수 279건·상세 60건의 응답 태그를
 * 전수 집계한 결과(realm2 12필드 · detail2 18태그)가 파서가 선언한 20필드와 정확히 일치한다
 * ({@code docs/개인 폴더/전시/공공데이터-제공률-실측과-DB빈값-분석.md}).
 * 이 payload가 있으면 변환 규칙이 바뀌어도(HTML 추출 사고의 교훈) 원천을 다시 부르지 않고 여기서 전량 재생성할 수 있다.
 *
 * <p>{@code payload_hash}로 원천이 값을 정정했는지 <b>행 단위</b>로 감지한다(페이지를 다시 파싱해 비교할 필요가 없다).
 * {@code last_seen_at}은 이번 동기화에도 원천에 있었는지 — 원천에서 사라진 항목 판별용이다.
 *
 * <p>{@code exhibitions}와는 ID·키 참조도 두지 않는다 — 벤더층은 도메인이 아직 적재하지 않은 항목(기간 불량으로
 * 스킵된 행 등)도 기록한다. 원본은 "원천이 뭐라고 했나"의 증거라 도메인 유효성과 생명주기가 다르다.
 * 감사 컬럼(created_at 등)이 필요 없어 {@link modi.backend.support.entity.BaseEntity}를 상속하지 않는다
 * ({@code first_seen_at}·{@code last_seen_at}이 그 역할을 도메인 언어로 대신한다).
 */
@Entity
@Table(name = "culture_list_response")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CultureListResponse {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, length = 100)
	private String externalId;

	/** realm2 응답 아이템의 매핑 JSON(도메인 변환 이전 값). */
	@Column(name = "payload", columnDefinition = "text")
	private String payload;

	/** payload의 SHA-256 hex(64자) — 원천 정정 감지용. */
	@Column(name = "payload_hash", length = 64)
	private String payloadHash;

	@Column(name = "first_seen_at")
	private LocalDateTime firstSeenAt;

	@Column(name = "last_seen_at")
	private LocalDateTime lastSeenAt;

	private CultureListResponse(String externalId, String payload, LocalDateTime now) {
		this.externalId = externalId;
		this.payload = payload;
		this.payloadHash = hash(payload);
		this.firstSeenAt = now;
		this.lastSeenAt = now;
	}

	/** 원천에서 처음 본 아이템. */
	public static CultureListResponse first(String externalId, String payload, LocalDateTime now) {
		return new CultureListResponse(externalId, payload, now);
	}

	/**
	 * 이번 동기화에도 원천에 있었다 — {@code last_seen_at}은 값이 그대로여도 항상 갱신한다("아직 살아 있다"는 사실 자체가 정보다).
	 * payload는 <b>해시가 달라졌을 때만</b> 덮는다 — 원천이 값을 정정한 경우다. 같은 값으로 매일 덮으면 UPDATE만
	 * 낭비되고, 무엇보다 "언제 바뀌었나"를 알 수 없게 된다.
	 */
	public void seenAgain(String payload, LocalDateTime now) {
		this.lastSeenAt = now;
		String incoming = hash(payload);
		if (incoming != null && !incoming.equals(this.payloadHash)) {
			this.payload = payload;
			this.payloadHash = incoming;
		}
	}

	/** 원천이 이 아이템의 값을 정정했는가 — 저장된 해시와 비교한다. */
	public boolean isChangedFrom(String payload) {
		String incoming = hash(payload);
		return incoming != null && !incoming.equals(this.payloadHash);
	}

	/**
	 * SHA-256 hex. payload가 null이면 null(해시할 원문이 없다 — "변경 없음"과 구분된다).
	 * SHA-256은 모든 JRE가 반드시 제공하므로 미지원은 발생할 수 없다.
	 */
	private static String hash(String payload) {
		if (payload == null) {
			return null;
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 미지원 JRE", e);
		}
	}
}
