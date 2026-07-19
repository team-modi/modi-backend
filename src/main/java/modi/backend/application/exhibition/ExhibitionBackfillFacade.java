package modi.backend.application.exhibition;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.application.exhibition.contract.DetailTargetState;
import modi.backend.application.exhibition.contract.ExhibitionBackfill;
import modi.backend.domain.exhibition.catalog.CatalogDetailData;
import modi.backend.domain.exhibition.catalog.Exhibition;
import modi.backend.domain.exhibition.catalog.ExhibitionGenre;
import modi.backend.domain.exhibition.catalog.ExhibitionPlace;
import modi.backend.domain.exhibition.catalog.ExhibitionPlaceRepository;
import modi.backend.domain.exhibition.catalog.ExhibitionRepository;
import modi.backend.domain.exhibition.genre.GenreClassification;
import modi.backend.domain.exhibition.genre.GenreResult;

/**
 * {@link ExhibitionBackfill} 구현 — 레거시 전시(이미 승격됐지만 상세/장르 미완성)의 조회·반영을 코어 애그리거트
 * 루트 경유로 수행한다(구 ExhibitionSyncFacade의 코어 접촉 메서드, ADR-12). 벤더 원본 보관·아웃박스 enqueue는
 * 수집 쪽이 같은 트랜잭션에서 잇는다(REQUIRED 전파).
 */
@Service
@RequiredArgsConstructor
public class ExhibitionBackfillFacade implements ExhibitionBackfill {

	private final ExhibitionRepository exhibitionRepository;
	private final ExhibitionPlaceRepository exhibitionPlaceRepository;

	@Override
	@Transactional(readOnly = true)
	public DetailTargetState findDetailTargetState(String externalId) {
		Exhibition exhibition = exhibitionRepository.findByExternalId(externalId).orElse(null);
		if (exhibition == null) {
			return DetailTargetState.MISSING;
		}
		return exhibitionRepository.hasDetail(exhibition.getId())
				? DetailTargetState.ALREADY_SYNCED : DetailTargetState.NEEDS_DETAIL;
	}

	@Override
	@Transactional
	public DetailApplied applyDetail(String externalId, CatalogDetailData d, LocalDateTime now) {
		Exhibition exhibition = exhibitionRepository.findByExternalId(externalId).orElse(null);
		if (exhibition == null || exhibitionRepository.hasDetail(exhibition.getId())) {
			return DetailApplied.skipped();
		}
		exhibitionRepository.applyDetail(exhibition.getId(), d.price(), d.description(), d.imgUrl(), now);
		String placeKey = exhibitionPlaceRepository.findById(exhibition.getExhibitionPlaceId())
				.map(place -> {
					place.enrichDetail(d.placeAddr(), d.phone(), d.placeUrl());
					exhibitionPlaceRepository.save(place);
					return place.getPlaceKey();
				}).orElse(null);
		return new DetailApplied(true, placeKey);
	}

	@Override
	@Transactional
	public void markDetailChecked(String externalId, LocalDateTime now) {
		Exhibition exhibition = exhibitionRepository.findByExternalId(externalId).orElse(null);
		if (exhibition == null || exhibitionRepository.hasDetail(exhibition.getId())) {
			return;
		}
		exhibitionRepository.markDetailChecked(exhibition.getId(), now);
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> findUnclassifiedCatalogExternalIds(int limit) {
		return exhibitionRepository.findCatalogWithoutGenre(limit).stream()
				.map(Exhibition::getExternalId)
				.filter(id -> id != null && !id.isBlank())
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, GenreClassification> resolveGenreInputs(Collection<String> externalIds) {
		List<Exhibition> exhibitions = exhibitionRepository.findAllByExternalIds(externalIds);
		if (exhibitions.isEmpty()) {
			return Map.of();
		}
		List<Long> ids = exhibitions.stream().map(Exhibition::getId).toList();
		Set<Long> classified = exhibitionRepository.findGenres(ids).stream()
				.map(ExhibitionGenre::getExhibitionId).collect(Collectors.toSet());
		Map<String, GenreClassification> inputs = new HashMap<>();
		for (Exhibition e : exhibitions) {
			if (!classified.contains(e.getId())) {
				inputs.put(e.getExternalId(), GenreClassification.from(e));
			}
		}
		return inputs;
	}

	@Override
	@Transactional
	public void applyGenreResults(Map<String, GenreResult> resultsByExternalId, LocalDateTime now) {
		if (resultsByExternalId.isEmpty()) {
			return;
		}
		List<Exhibition> exhibitions = exhibitionRepository.findAllByExternalIds(resultsByExternalId.keySet());
		for (Exhibition e : exhibitions) {
			GenreResult result = resultsByExternalId.get(e.getExternalId());
			if (result != null) {
				exhibitionRepository.applyGenre(e.getId(), result, now);
			}
		}
	}

}
