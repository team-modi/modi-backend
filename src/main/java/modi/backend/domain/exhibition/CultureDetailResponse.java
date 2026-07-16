package modi.backend.domain.exhibition;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한눈에보는문화정보 상세(detail2) 응답 원본 + 수집 상태기계(벤더층) — {@code culture_detail_response} 매핑.
 *
 * <p>단일 원천이라 이 자리가 곧 정준이다(별도 정준 테이블 ❌ — 중간을 넣으면 컬럼이 1:1로 흐르는 순수 패스스루가 된다).
 * {@link CultureListResponse}와 {@code external_id}로 짝을 이룬다.
 *
 * <p>{@code payload}는 응답 아이템을 매핑해 직렬화한 JSON(도메인 변환 이전 값)이다 — 특히 상세의 {@code contents1}은
 * 원천이 워드프레스 블록/HTML로 내려주는데 그 <b>원문이 그대로 보존</b>되므로, 평문 추출 규칙이 바뀌면 여기서
 * 재추출한다(원천 재호출 없이). 응답 구조가 실측으로 확정돼 있어 이 직렬화는 무손실이다({@link CultureListResponse} 참조).
 *
 * <p>{@code attempt_count}·{@code next_attempt_at}이 푸는 문제: 현행은 재시도 상태가 없어 영구 실패 행이 매 주기
 * 무한 재시도된다. 다만 <b>이관 3단계에서는 쓰기만 하고 아무도 읽지 않는다</b>(대상 선별은 여전히
 * {@code exhibitions.detail_synced_at}이 맡는다) — 구조 이관과 동작 변경을 한 번에 하면 회귀 원인을 분리할 수 없기 때문이다.
 */
@Entity
@Table(name = "culture_detail_response")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CultureDetailResponse {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, length = 100)
	private String externalId;

	/** detail2 응답 아이템의 매핑 JSON. 상세가 없거나(NO_DATA) 실패면(FAILED) null이다. */
	@Column(name = "payload", columnDefinition = "text")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private CultureDetailStatus status;

	/** 지금까지의 detail2 호출 시도 횟수(성공 포함) — "몇 번 만에 됐나"·영구 실패 판별의 재료. */
	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	/**
	 * 다음 재시도 도래 시각. <b>이관 3단계에서는 항상 null이다</b> — 백오프 정책은 이 테이블이 대상 선별을 맡는
	 * 단계에서 정한다. 읽는 곳이 없는 지금 값을 지어내면, 나중에 그 값이 근거 있는 정책인 줄 알고 쓰게 된다.
	 */
	@Column(name = "next_attempt_at")
	private LocalDateTime nextAttemptAt;

	private CultureDetailResponse(String externalId, String payload, CultureDetailStatus status) {
		this.externalId = externalId;
		this.payload = payload;
		this.status = status;
		this.attemptCount = 1;
	}

	/** 원천이 상세를 준 첫 응답. */
	public static CultureDetailResponse succeeded(String externalId, String payload) {
		return new CultureDetailResponse(externalId, payload, CultureDetailStatus.SUCCEEDED);
	}

	/** 원천에 상세가 없는 첫 응답(빈 응답). */
	public static CultureDetailResponse noData(String externalId) {
		return new CultureDetailResponse(externalId, null, CultureDetailStatus.NO_DATA);
	}

	/** 전송 실패 등 첫 일시 실패. */
	public static CultureDetailResponse failed(String externalId) {
		return new CultureDetailResponse(externalId, null, CultureDetailStatus.FAILED);
	}

	/**
	 * 다시 호출해 상세를 받았다. 시도 횟수를 늘리고 원본·상태를 갱신한다.
	 * 실패했다 성공하는 경로가 정상이므로 상태 되돌림(FAILED → SUCCEEDED)은 허용된다.
	 */
	public void recordSucceeded(String payload) {
		this.attemptCount++;
		this.payload = payload;
		this.status = CultureDetailStatus.SUCCEEDED;
	}

	/**
	 * 다시 호출했더니 원천에 상세가 없었다. 이전 payload는 <b>지우지 않는다</b> — 한 번이라도 받아둔 원문은
	 * 재파싱 원료라, 원천이 일시적으로 비워 보냈다고 우리가 증거를 버릴 이유가 없다.
	 */
	public void recordNoData() {
		this.attemptCount++;
		this.status = CultureDetailStatus.NO_DATA;
	}

	/** 다시 호출했더니 실패했다. 같은 이유로 이전 payload는 보존한다. */
	public void recordFailed() {
		this.attemptCount++;
		this.status = CultureDetailStatus.FAILED;
	}
}
