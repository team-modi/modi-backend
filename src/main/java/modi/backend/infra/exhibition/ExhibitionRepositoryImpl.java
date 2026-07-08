package modi.backend.infra.exhibition;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.Exhibition;
import modi.backend.domain.exhibition.ExhibitionQuery;
import modi.backend.domain.exhibition.ExhibitionRepository;
import modi.backend.domain.exhibition.ExhibitionType;

/**
 * {@link ExhibitionRepository} 어댑터(DIP). Spring Data + Specification으로 위임하며, 조회는 살아있는 행만 본다.
 * 목록은 키셋(커서) 페이지네이션 — 정렬은 (정렬컬럼, id) 조합이며 nulls last로 결정적이다.
 */
@Repository
@RequiredArgsConstructor
public class ExhibitionRepositoryImpl implements ExhibitionRepository {

	private final ExhibitionJpaRepository jpaRepository;

	@Override
	public Exhibition save(Exhibition exhibition) {
		return jpaRepository.save(exhibition);
	}

	@Override
	public Optional<Exhibition> findById(Long id) {
		return jpaRepository.findById(id).filter(exhibition -> exhibition.getDeletedAt() == null);
	}

	@Override
	public List<Exhibition> searchSlice(ExhibitionQuery query, int limitPlusOne) {
		return jpaRepository.findAll(ExhibitionSpecifications.slice(query),
				PageRequest.of(0, limitPlusOne, sortFor(query.sort()))).getContent();
	}

	@Override
	public long count(ExhibitionQuery query) {
		return jpaRepository.count(ExhibitionSpecifications.filter(query));
	}

	@Override
	public List<Exhibition> searchAll(ExhibitionQuery query) {
		return jpaRepository.findAll(ExhibitionSpecifications.filter(query));
	}

	@Override
	public Optional<Exhibition> findByExternalId(String externalId) {
		return jpaRepository.findByExternalIdAndDeletedAtIsNull(externalId);
	}

	@Override
	public List<Exhibition> findAllActiveByIds(Collection<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return jpaRepository.findAllById(ids).stream()
				.filter(exhibition -> exhibition.getDeletedAt() == null)
				.toList();
	}

	@Override
	public List<Exhibition> findCatalogWithoutGenre(int limit) {
		return jpaRepository.findByTypeAndGenreKeywordIsNullAndDeletedAtIsNull(
				ExhibitionType.CATALOG, PageRequest.of(0, Math.max(1, limit)));
	}

	@Override
	public List<Exhibition> findCatalogWithoutDetail(int limit) {
		return jpaRepository.findByTypeAndDetailSyncedAtIsNullAndDeletedAtIsNull(
				ExhibitionType.CATALOG, PageRequest.of(0, Math.max(1, limit)));
	}

	@Override
	public List<Exhibition> findOngoingCatalogTopByViews(LocalDate onDate, int limit) {
		return jpaRepository
				.findByTypeAndStartDateLessThanEqualAndEndDateGreaterThanEqualAndDeletedAtIsNullOrderByOurViewCountDesc(
						ExhibitionType.CATALOG, onDate, onDate, PageRequest.of(0, Math.max(1, limit)));
	}

	/** sort 코드 → (정렬컬럼 nulls last, id) 결정적 정렬. 키셋 경계 조건과 순서가 일치해야 한다. */
	private static Sort sortFor(String sort) {
		return switch (sort == null ? "latest" : sort) {
			case "ending" -> Sort.by(Sort.Order.asc("endDate"), Sort.Order.asc("id"));
			case "popular" -> Sort.by(Sort.Order.desc("ourViewCount"), Sort.Order.desc("id"));
			default -> Sort.by(Sort.Order.desc("startDate"), Sort.Order.desc("id"));
		};
	}
}
