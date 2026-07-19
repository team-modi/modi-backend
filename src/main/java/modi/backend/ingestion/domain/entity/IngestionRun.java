package modi.backend.ingestion.domain.entity;

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
import modi.backend.ingestion.domain.SyncTrigger;

/**
 * 한 번의 카탈로그 동기화 실행 기록(append-only) — {@code ingestion_run} 매핑.
 *
 * <p><b>왜 {@code external_api_call}의 컬럼이 아니라 별도 테이블인가</b>: 절단은 <b>배치 단위 사실</b>이다.
 * 목록 3콜은 각자 200 OK로 멀쩡히 성공하는데도 원천의 4번째 페이지를 안 부른 것이 절단이라, 개별 호출 행의
 * {@code outcome}으로는 표현할 자리가 없다(그 호출은 SUCCESS다). 두 테이블의 역할이 갈린다 —
 * {@code external_api_call}은 "무엇을 몇 번 불렀나·얼마를 태웠나", {@code ingestion_run}은 "이번 실행이 원천을 다 가져왔나".
 *
 * <p>무엇을 푸나: 현행은 응답의 {@code totalCount}를 파싱만 하고 <b>버린다</b>. 그래서
 * {@code max-pages 5 × num-of-rows 100 = 500건} 상한을 넘는 순간 <b>아무 로그도 없이 조용히 절단</b>된다.
 * 2026-07-15 실측 totalCount=280이라 지금은 여유가 있지만, 원천이 500을 넘기는 날 우리는 알 수 없다.
 *
 * <p>집계 값들은 {@code syncCatalog}가 이미 계산해 <b>로그로만 흘려보내던</b> 것이다. 로그는 질의할 수 없어
 * 추이도 회귀도 볼 수 없다 — 같은 값을 행으로 남긴다.
 */
@Entity
@Table(name = "ingestion_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestionRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 이 실행을 촉발한 계기(BOOT/SCHEDULE/MANUAL) — "왜 이 시각에 돌았나"를 남긴다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "trigger_type", nullable = false, length = 20)
	private SyncTrigger triggerType;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	/** 원천이 말한 총 건수. 인증키 미설정 등으로 호출이 없었으면 null = "모른다"(0이 아니다). */
	@Column(name = "total_count")
	private Integer totalCount;

	/** 원천에 더 있는데 상한(max-pages)에 걸려 못 가져왔나 — 이 테이블의 존재 이유다. */
	@Column(name = "truncated", nullable = false)
	private boolean truncated;

	@Column(name = "collected", nullable = false)
	private int collected;

	@Column(name = "inserted", nullable = false)
	private int inserted;

	@Column(name = "completed", nullable = false)
	private int completed;

	@Column(name = "skipped", nullable = false)
	private int skipped;

	@Column(name = "deferred", nullable = false)
	private int deferred;

	private IngestionRun(SyncTrigger triggerType, LocalDateTime startedAt) {
		this.triggerType = triggerType;
		this.startedAt = startedAt;
	}

	/** 실행 시작 — 수집 결과를 받기 전 상태. 계기(trigger)를 함께 남긴다. */
	public static IngestionRun started(SyncTrigger triggerType, LocalDateTime startedAt) {
		return new IngestionRun(triggerType, startedAt);
	}

	/** 수집 결과(원천이 말한 총 건수·절단 여부·수집 건수)를 기록한다. */
	public void fetched(Integer totalCount, boolean truncated, int collected) {
		this.totalCount = totalCount;
		this.truncated = truncated;
		this.collected = collected;
	}

	/** 적재 집계와 종료 시각을 기록한다. */
	public void finished(int inserted, int completed, int skipped, int deferred, LocalDateTime finishedAt) {
		this.inserted = inserted;
		this.completed = completed;
		this.skipped = skipped;
		this.deferred = deferred;
		this.finishedAt = finishedAt;
	}
}
