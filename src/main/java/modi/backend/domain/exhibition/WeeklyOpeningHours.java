package modi.backend.domain.exhibition;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 한 장소의 요일별 영업시간(도메인 값 객체). 구글 응답(periods)이나 mock을 파싱한 결과이며,
 * {@link OpeningHoursFormatter}가 이 값을 우리 표시 규칙 문자열로 바꾼다.
 * <p>
 * {@code byDay}에 <b>영업하는 요일만</b> 담는다(시간범위 1개 이상). 맵에 없는 요일 = 휴무.
 * 하루에 여러 구간(예: 점심 브레이크)도 지원한다. 저장은 하지 않는 순수 도메인 값(@Embeddable 아님).
 */
public record WeeklyOpeningHours(Map<DayOfWeek, List<TimeRange>> byDay) {

	/** 하루의 한 영업 구간. open ≤ close 를 권장하나 강제하지 않는다(원천 특이값 방어는 상위에서). */
	public record TimeRange(LocalTime open, LocalTime close) {
		public TimeRange {
			Objects.requireNonNull(open, "open");
			Objects.requireNonNull(close, "close");
		}
	}

	public WeeklyOpeningHours {
		// 영업 요일만(빈 구간 제거) + 각 요일 구간은 open 시각 오름차순으로 정규화해 결정적 출력이 되게 한다.
		Map<DayOfWeek, List<TimeRange>> normalized = new EnumMap<>(DayOfWeek.class);
		if (byDay != null) {
			for (Map.Entry<DayOfWeek, List<TimeRange>> e : byDay.entrySet()) {
				if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
					continue;
				}
				List<TimeRange> ranges = new ArrayList<>(e.getValue());
				ranges.sort(Comparator.comparing(TimeRange::open).thenComparing(TimeRange::close));
				normalized.put(e.getKey(), List.copyOf(ranges));
			}
		}
		byDay = Map.copyOf(normalized);
	}

	/** 영업 요일이 하나도 없으면(정보 없음/전 요일 휴무) true. */
	public boolean hasNoOpenDay() {
		return byDay.isEmpty();
	}

	/** 빈 영업시간(정보 없음). */
	public static WeeklyOpeningHours empty() {
		return new WeeklyOpeningHours(Map.of());
	}

	/** 가변 맵을 만들어 요일별로 구간을 추가하는 빌더. */
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private final Map<DayOfWeek, List<TimeRange>> byDay = new EnumMap<>(DayOfWeek.class);

		/** 해당 요일에 영업 구간 하나를 추가한다. */
		public Builder add(DayOfWeek day, LocalTime open, LocalTime close) {
			byDay.computeIfAbsent(day, d -> new ArrayList<>()).add(new TimeRange(open, close));
			return this;
		}

		public WeeklyOpeningHours build() {
			return new WeeklyOpeningHours(byDay);
		}
	}
}
