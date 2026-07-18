package modi.backend.domain.exhibition.catalog;

import java.time.LocalDate;

import modi.backend.domain.exhibition.hours.PlaceKey;

/**
 * 테스트 픽스처 헬퍼 — 전시장 분리(exhibition_place N:1) 이후 테스트가 전시를 만들 때마다 전시장부터 resolve-or-create해야 하는
 * 반복을 줄인다. 도메인 규칙(정규화 이름 upsert)을 프로덕션과 동일하게 재사용한다.
 */
public final class ExhibitionTestFactory {

	private ExhibitionTestFactory() {
	}

	/** 전시장 resolve-or-create 후 id 반환(정규화 이름 기준). */
	public static Long placeId(ExhibitionPlaceRepository repository, String name, ExhibitionRegion region) {
		return repository.findByPlaceKey(PlaceKey.of(name))
				.orElseGet(() -> repository.save(ExhibitionPlace.createFromList(name, region, null, null, null)))
				.getId();
	}

	/** 지정 전시장에 소속된 CATALOG 전시 하나를 만든다(영속화는 호출부). */
	public static Exhibition catalog(Long placeId, String externalId, String title, LocalDate startDate,
			LocalDate endDate, ExhibitionCategory category) {
		return Exhibition.createCatalog(externalId, title, placeId, startDate, endDate, category, null, null, "기관");
	}
}
