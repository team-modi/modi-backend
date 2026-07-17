package modi.backend.domain.exhibition;

import java.util.List;

/**
 * 전시-작가 조인 저장소 포트(Spring 무의존). 조인 생성(멱등)과 전시별 작가 이름 조립을 노출한다.
 */
public interface ExhibitionArtistRepository {

	ExhibitionArtist save(ExhibitionArtist exhibitionArtist);

	boolean existsByExhibitionIdAndArtistId(Long exhibitionId, Long artistId);

	/** 한 전시의 작가 이름들을 조인 순서대로 조립한다(상세 응답 artists·artistSummary 조립용). 없으면 빈 목록. */
	List<String> findArtistNames(Long exhibitionId);
}
