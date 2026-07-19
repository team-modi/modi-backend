package modi.backend.domain.exhibition.sync.draft;

/**
 * 전시 초기화 스테이징({@link ExhibitionDraft})의 라이프사이클 — 초기화 in-flight 상태의 단일 어휘(ADR-10).
 *
 * <pre>
 * PENDING ──(첫 스텝 반영)──▶ ENRICHING ──(필수 스텝 완주·승격)──▶ COMPLETED
 *                                   ╲──(필수 스텝 영구 실패)──▶ FAILED
 * </pre>
 *
 * <p>COMPLETED/FAILED는 종료 상태다. FAILED는 운영자 가시화 대상이고, 재sync가 자동으로 되살리지 않는다
 * (영구 실패는 다시 시도해도 같은 결과 — 수동 개입으로만 되살아난다).
 */
public enum DraftStatus {

	/** 목록으로 스테이징됨 — 보강 스텝(상세→장르)이 아직 반영되지 않았다. */
	PENDING,

	/** 보강 진행 중 — 최소 한 스텝이 반영됐고 승격 게이트를 아직 채우지 못했다. */
	ENRICHING,

	/** 승격 완료 — 진짜 {@code Exhibition}이 생성됐다(추적: promoted_exhibition_id). 종료 상태. */
	COMPLETED,

	/** 필수 스텝의 영구 실패(4xx·시도 소진) — 승격 불가. 운영자 조회 대상. 종료 상태. */
	FAILED;

	/** 더 손댈 일이 없는 종료 상태인가. */
	public boolean isTerminal() {
		return this == COMPLETED || this == FAILED;
	}
}
