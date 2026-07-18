package modi.backend.application.bookmark;

import java.util.List;

import modi.backend.application.exhibition.ExhibitionResult;

/**
 * 관심 전시(북마크) 유스케이스 출력 모음. (Facade는 Result까지만)
 * 목록 항목은 전시 목록과 동일 스키마를 공유하므로 {@link ExhibitionResult.ListItem}을 재사용한다(bookmarked 항상 true).
 */
public final class BookmarkResult {

	private BookmarkResult() {
	}

	/** 토글 결과 — 대상 전시 id와 현재 관심 여부(등록 true / 해제 false). */
	public record Toggle(Long exhibitionId, boolean bookmarked) {
	}

	/** 관심 전시 한 페이지(커서 페이지네이션 shape). content 항목은 전시 목록 항목과 동일. */
	public record ListPage(List<ExhibitionResult.ListItem> content, String nextCursor, boolean hasNext,
			long totalCount) {
	}
}
