package modi.backend.domain.exhibition;

import java.util.Optional;

/** 구글 Places 응답 원본 저장소 포트(Spring 무의존). */
public interface GooglePlaceResponseRepository {

	GooglePlaceResponse save(GooglePlaceResponse googlePlaceResponse);

	Optional<GooglePlaceResponse> findByExhibitionPlaceId(Long exhibitionPlaceId);
}
