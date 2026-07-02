package modi.backend.domain.record;

import java.time.LocalDate;

import modi.backend.support.error.CoreException;
import modi.backend.support.error.ErrorType;

/** 기록 생성 시점의 전시 표시정보 박제(문자열 — 전시 enum 비의존). */
public record ExhibitionSnapshot(String title, String type, String posterUrl, String place,
		String region, String category, LocalDate startDate, LocalDate endDate) {
	public ExhibitionSnapshot {
		if (title == null || title.isBlank())
			throw new CoreException(ErrorType.INVALID_INPUT, "전시 제목 스냅샷은 필수입니다.");
	}
}
