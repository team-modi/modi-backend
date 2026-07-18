package modi.backend.application.bookmark;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import modi.backend.application.exhibition.serving.ExhibitionResult;
import modi.backend.domain.bookmark.ExhibitionBookmarkRepository;
import modi.backend.domain.exhibition.catalog.Exhibition;
import modi.backend.domain.exhibition.catalog.ExhibitionDetail;
import modi.backend.domain.exhibition.catalog.ExhibitionErrorCode;
import modi.backend.domain.exhibition.catalog.ExhibitionPlace;
import modi.backend.domain.exhibition.catalog.ExhibitionPlaceRepository;
import modi.backend.domain.exhibition.catalog.ExhibitionRepository;
import modi.backend.support.error.CoreException;
import modi.backend.support.response.Cursor;
import modi.backend.support.time.AppTime;

/**
 * 관심 전시(북마크) 유스케이스 조율(북마크 6.1~6.3). load·조율·save만 한다.
 * 토글은 멱등이며(add/remove가 멱등), 전시 존재 검증 후 위임한다. 목록은 활성 북마크 id를 모아 전시를 벌크 로드하고
 * sort에 맞게 앱 레이어에서 정렬·커서 슬라이스한다(북마크 수가 적어 인메모리 키셋으로 충분).
 */
@Service
@RequiredArgsConstructor
public class BookmarkFacade {

	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 50;

	private final ExhibitionBookmarkRepository exhibitionBookmarkRepository;
	private final ExhibitionRepository exhibitionRepository;
	private final ExhibitionPlaceRepository exhibitionPlaceRepository;

	/** 관심 등록(6.1, 멱등). 없는 전시면 404. 반환은 항상 bookmarked=true. */
	@Transactional
	public BookmarkResult.Toggle add(BookmarkCriteria.Toggle criteria) {
		ensureExhibitionExists(criteria.exhibitionId());
		exhibitionBookmarkRepository.add(criteria.userId(), criteria.exhibitionId());
		return new BookmarkResult.Toggle(criteria.exhibitionId(), true);
	}

	/** 관심 해제(6.2, 멱등). 없는 전시면 404. 반환은 항상 bookmarked=false. */
	@Transactional
	public BookmarkResult.Toggle remove(BookmarkCriteria.Toggle criteria) {
		ensureExhibitionExists(criteria.exhibitionId());
		exhibitionBookmarkRepository.remove(criteria.userId(), criteria.exhibitionId());
		return new BookmarkResult.Toggle(criteria.exhibitionId(), false);
	}

	/**
	 * 관심 전시 목록(6.3). sort=latest는 등록 최신순, sort=ending은 종료 임박순(종료일 asc, null 뒤로).
	 * 커서는 정렬 판별자(sort)를 검증하며(불일치 → INVALID_CURSOR), 마지막 항목 id 위치 기준으로 슬라이스한다.
	 */
	@Transactional(readOnly = true)
	public BookmarkResult.ListPage list(BookmarkCriteria.List criteria) {
		LocalDate today = LocalDate.now(AppTime.KST);
		String sort = canonicalSort(criteria.sort());
		int size = clampSize(criteria.size());

		List<Long> orderedIds = exhibitionBookmarkRepository
				.findActiveExhibitionIdsByUserIdOrderByRegisteredDesc(criteria.userId());
		Map<Long, Exhibition> byId = new LinkedHashMap<>();
		for (Exhibition e : exhibitionRepository.findAllActiveByIds(orderedIds)) {
			byId.put(e.getId(), e);
		}
		List<Exhibition> ordered = orderPerSort(sort, orderedIds, byId);

		Cursor cursor = Cursor.decode(criteria.cursor(), sort).orElse(null);
		int start = cursor == null ? 0 : nextIndexAfter(ordered, cursor.lastId());
		int end = Math.min(start + size, ordered.size());
		List<Exhibition> page = start >= ordered.size() ? List.of() : ordered.subList(start, end);
		boolean hasNext = end < ordered.size();

		Map<Long, ExhibitionPlace> placesById = exhibitionPlaceRepository.findAllByIds(
				page.stream().map(Exhibition::getExhibitionPlaceId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(ExhibitionPlace::getId, p -> p, (a, b) -> a));
		Map<Long, ExhibitionDetail> detailsByExhibitionId = exhibitionRepository.findDetails(
				page.stream().map(Exhibition::getId).toList()).stream()
				.collect(Collectors.toMap(ExhibitionDetail::getExhibitionId, d -> d, (a, b) -> a));
		List<ExhibitionResult.ListItem> content = page.stream()
				.map(e -> {
					ExhibitionDetail detail = detailsByExhibitionId.get(e.getId());
					boolean free = detail != null && detail.isFree();
					return ExhibitionResult.ListItem.from(e, placesById.get(e.getExhibitionPlaceId()), today, free,
							true);
				})
				.toList();
		String nextCursor = null;
		if (hasNext) {
			Exhibition last = page.get(page.size() - 1);
			String key = "ending".equals(sort)
					? (last.getEndDate() == null ? null : last.getEndDate().toString())
					: null;
			nextCursor = Cursor.of(sort, key, last.getId()).encode();
		}
		return new BookmarkResult.ListPage(content, nextCursor, hasNext, ordered.size());
	}

	private void ensureExhibitionExists(Long exhibitionId) {
		if (exhibitionRepository.findById(exhibitionId).isEmpty()) {
			throw new CoreException(ExhibitionErrorCode.EXHIBITION_NOT_FOUND);
		}
	}

	/** latest는 등록 최신순(orderedIds 순서 보존), ending은 종료일 asc(null 뒤로)·id asc. 삭제된 전시 id는 자동 제외. */
	private static List<Exhibition> orderPerSort(String sort, List<Long> orderedIds, Map<Long, Exhibition> byId) {
		if ("ending".equals(sort)) {
			return byId.values().stream()
					.sorted(Comparator
							.comparing(Exhibition::getEndDate, Comparator.nullsLast(Comparator.naturalOrder()))
							.thenComparing(Exhibition::getId))
					.toList();
		}
		return orderedIds.stream().map(byId::get).filter(e -> e != null).toList();
	}

	/** 정렬된 리스트에서 lastId 항목 다음 인덱스. 못 찾으면(데이터 변동) 0(처음부터). */
	private static int nextIndexAfter(List<Exhibition> ordered, Long lastId) {
		for (int i = 0; i < ordered.size(); i++) {
			if (ordered.get(i).getId().equals(lastId)) {
				return i + 1;
			}
		}
		return 0;
	}

	/** sort 코드 → 정규화(latest 기본). 미정의 값은 latest로 취급(커서 정렬 판별자도 이 값으로 통일). */
	private static String canonicalSort(String sort) {
		if (sort == null) {
			return "latest";
		}
		return "ending".equalsIgnoreCase(sort.trim()) ? "ending" : "latest";
	}

	private static int clampSize(Integer size) {
		if (size == null || size < 1) {
			return DEFAULT_SIZE;
		}
		return Math.min(size, MAX_SIZE);
	}
}
