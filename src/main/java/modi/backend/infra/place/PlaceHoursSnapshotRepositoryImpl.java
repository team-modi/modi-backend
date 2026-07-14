package modi.backend.infra.place;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.PlaceHoursSnapshot;
import modi.backend.domain.exhibition.PlaceHoursSnapshotRepository;

/** {@link PlaceHoursSnapshotRepository} 어댑터(포트↔Spring Data). */
@Repository
@RequiredArgsConstructor
public class PlaceHoursSnapshotRepositoryImpl implements PlaceHoursSnapshotRepository {

	private final PlaceHoursSnapshotJpaRepository jpaRepository;

	@Override
	public PlaceHoursSnapshot save(PlaceHoursSnapshot snapshot) {
		return jpaRepository.save(snapshot);
	}

	@Override
	public void deleteAllSnapshots() {
		// 스테이징 전체 비움(초기화). 벌크 삭제로 행 단위 이벤트 없이 한 번에 지운다.
		jpaRepository.deleteAllInBatch();
	}
}
