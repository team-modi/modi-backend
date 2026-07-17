package modi.backend.domain.exhibition;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 전시장 저장소 포트(Spring 무의존). resolve-or-create(정규화 이름 upsert)와 목록/상세 조립용 벌크 조회를 노출한다.
 */
public interface ExhibitionPlaceRepository {

	ExhibitionPlace save(ExhibitionPlace place);

	Optional<ExhibitionPlace> findById(Long id);

	/** 자연키(정규화 이름)로 조회 — resolve-or-create 진입점. */
	Optional<ExhibitionPlace> findByPlaceKey(String placeKey);

	/** 여러 전시장을 id로 일괄 조회(목록·상세의 장소 필드 조립, N+1 방지). 빈 입력이면 빈 목록. */
	List<ExhibitionPlace> findAllByIds(Collection<Long> ids);

	/**
	 * 영업시간 보강 대상 전시장 — 주소가 있고, 아직 영업시간 정준행이 없거나({@code place_hours} 부재)
	 * {@code staleBefore}보다 오래 전에 동기화된 전시장을 최대 {@code limit}건(정렬 결정적: id asc).
	 * 장소당 1행이 곧 유료 호출 1회다(ADR-06 dedup).
	 */
	List<ExhibitionPlace> findPlacesNeedingHours(LocalDateTime staleBefore, int limit);
}
