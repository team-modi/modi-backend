package modi.backend.domain.exhibition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 전시 장르 정준 저장소 포트(Spring 무의존).
 *
 * <p>포트 메서드명에 필터 조건(soft-delete·타입)을 드러내지 않는다 — 그건 구현 세부이고, 도메인 언어에 새면
 * 저장 방식이 바뀔 때 포트까지 흔들린다. 필터는 어댑터(Impl)가 주입한다.
 */
public interface ExhibitionGenreRepository {

	ExhibitionGenre save(ExhibitionGenre exhibitionGenre);

	Optional<ExhibitionGenre> findByExhibitionId(Long exhibitionId);

	/**
	 * 여러 전시의 장르를 한 번에 읽는다(N+1 방지). 목록 응답엔 장르가 나가지 않으므로 현재는 상세·배치 경로용이다.
	 */
	List<ExhibitionGenre> findAllByExhibitionIds(Collection<Long> exhibitionIds);
}
