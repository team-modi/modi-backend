package modi.backend.domain.exhibition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 장소 영업시간 정준 저장소 포트(Spring 무의존).
 * 포트 메서드명에 필터·저장 방식 세부를 드러내지 않는다 — 그건 어댑터(Impl)의 몫이다.
 */
public interface PlaceHoursRepository {

	PlaceHours save(PlaceHours placeHours);

	Optional<PlaceHours> findByExhibitionPlaceId(Long exhibitionPlaceId);

	/** 여러 장소의 영업시간을 한 번에 읽는다(N+1 방지). 목록엔 영업시간이 나가지 않아 현재는 상세·배치 경로용이다. */
	List<PlaceHours> findAllByExhibitionPlaceIds(Collection<Long> exhibitionPlaceIds);
}
