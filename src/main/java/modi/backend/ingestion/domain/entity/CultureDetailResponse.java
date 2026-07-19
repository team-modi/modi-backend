package modi.backend.ingestion.domain.entity;

import modi.backend.ingestion.domain.outbox.OutboxMessage;
import modi.backend.ingestion.domain.outbox.OutboxMessageType;

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
 * 한눈에보는문화정보 상세(detail2) 응답 <b>원본 보관소</b>(벤더층) — {@code culture_detail_response} 매핑.
 *
 * <p><b>순수 원본 보관소로 회귀했다</b>(설계 §2, ADR-01). 예전엔 이 테이블이 상태머신
 * ({@code status/attempt_count/next_attempt_at})까지 이고 있었는데 <b>그 컬럼은 쓰기만 되고 아무도 읽지 않았다</b>
 * (현행 최대 갭). 진행 상태·재시도는 전시 아웃박스({@link OutboxMessage}, {@link OutboxMessageType#FETCH_DETAIL})로 이관했고
 * (V27 create → V28 backfill → V29 drop), 벤더 테이블은 "원천이 뭐라고 답했나"의 무손실 원본만 남긴다.
 *
 * <p>{@code payload}는 응답 아이템을 매핑해 직렬화한 JSON이다 — 특히 상세의 {@code contents1}은 원천이
 * 워드프레스 블록/HTML로 내려주는데 그 <b>원문이 그대로 보존</b>되므로, 평문 추출 규칙이 바뀌면 여기서 재추출한다
 * (원천 재호출 없이). 원본이 없는 응답(원천에 상세가 없거나 조회 실패)은 남길 원본이 없어 <b>행을 만들지 않는다</b>
 * (그 사실은 각각 {@code exhibitions.detail_synced_at}과 FETCH_DETAIL 작업이 안다).
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

	/** detail2 응답 아이템의 매핑 JSON(무손실 원본). */
	@Column(name = "payload", columnDefinition = "text")
	private String payload;

	private CultureDetailResponse(String externalId, String payload) {
		this.externalId = externalId;
		this.payload = payload;
	}

	/** 원천이 상세를 준 첫 응답의 원본을 보관한다. */
	public static CultureDetailResponse first(String externalId, String payload) {
		return new CultureDetailResponse(externalId, payload);
	}

	/** 재조회로 받은 최신 원본으로 갱신한다(멱등 upsert — 같은 external_id는 항상 1행). */
	public void refresh(String payload) {
		this.payload = payload;
	}
}
