package modi.backend.ingestion.domain.draft;

import java.util.Optional;

/**
 * 전시 초기화 스테이징 저장 포트(Spring 무의존).
 *
 * <p>draft의 조회 축은 원천키 하나다 — 스테이징(재sync 갱신)·스텝 반영(핸들러)·승격이 전부
 * {@code external_id}로 대상을 해소한다(아웃박스 target_key와 같은 어휘).
 */
public interface ExhibitionDraftRepository {

	ExhibitionDraft save(ExhibitionDraft draft);

	/** 원천키로 draft를 찾는다(UK로 최대 1건). */
	Optional<ExhibitionDraft> findByExternalId(String externalId);

	/** 상태별 개수(운영 조회·테스트용 — 예: FAILED 누적 감시). */
	long countByStatus(DraftStatus status);
}
