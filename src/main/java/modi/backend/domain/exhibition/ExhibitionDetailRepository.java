package modi.backend.domain.exhibition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 전시 상세 satellite 저장소 포트(Spring 무의존). 상세 upsert(1:1)와 목록/재파싱용 벌크 조회를 노출한다.
 */
public interface ExhibitionDetailRepository {

	ExhibitionDetail save(ExhibitionDetail detail);

	Optional<ExhibitionDetail> findByExhibitionId(Long exhibitionId);

	/** 상세 동기화 여부 판정(행 존재 = 동기화됨). */
	boolean existsByExhibitionId(Long exhibitionId);

	/** 여러 전시의 상세를 한 번에 조회(N+1 방지). 빈 입력이면 빈 목록. */
	List<ExhibitionDetail> findAllByExhibitionIds(Collection<Long> exhibitionIds);

	/** 설명이 있는 상세를 모두 조회(재파싱 대상). */
	List<ExhibitionDetail> findAllWithDescription();
}
