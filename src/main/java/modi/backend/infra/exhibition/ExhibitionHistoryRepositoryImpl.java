package modi.backend.infra.exhibition;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.ExhibitionHistory;
import modi.backend.domain.exhibition.ExhibitionHistoryRepository;

@Repository
@RequiredArgsConstructor
public class ExhibitionHistoryRepositoryImpl implements ExhibitionHistoryRepository {

	private final ExhibitionHistoryJpaRepository jpaRepository;

	@Override
	public ExhibitionHistory save(ExhibitionHistory exhibitionHistory) {
		return jpaRepository.save(exhibitionHistory);
	}

	@Override
	public List<ExhibitionHistory> findByExhibitionId(Long exhibitionId) {
		// 같은 수정 이벤트(같은 edited_at) 안에서는 id 순으로 안정 정렬해 순서가 흔들리지 않게 한다.
		return jpaRepository.findByExhibitionIdOrderByEditedAtAscIdAsc(exhibitionId);
	}
}
