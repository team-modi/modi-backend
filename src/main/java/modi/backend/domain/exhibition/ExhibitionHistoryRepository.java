package modi.backend.domain.exhibition;

import java.util.List;

/**
 * 전시 수정 이력 저장 포트(Spring 무의존). append-only라 저장과 조회만 제공한다.
 */
public interface ExhibitionHistoryRepository {

	ExhibitionHistory save(ExhibitionHistory exhibitionHistory);

	/** 한 전시의 수정 이력을 시간순으로 읽는다(최신이 뒤). */
	List<ExhibitionHistory> findByExhibitionId(Long exhibitionId);
}
