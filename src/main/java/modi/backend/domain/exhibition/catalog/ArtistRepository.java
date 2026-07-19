package modi.backend.domain.exhibition.catalog;

import java.util.Optional;

/**
 * 작가 저장소 포트(Spring 무의존). 자연키(정규화 이름)로 resolve-or-create 하는 데 필요한 최소 연산만 노출한다.
 */
public interface ArtistRepository {

	Artist save(Artist artist);

	/** 정규화한 이름으로 기존 작가 조회(없으면 생성 — resolve-or-create). */
	Optional<Artist> findByName(String normalizedName);
}
