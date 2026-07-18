package modi.backend.domain.exhibition.hours;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * {@link WeeklyOpeningHours}를 전시 상세에 노출할 표시 문자열로 변환한다(도메인 규칙, 순수 로직).
 * <p>
 * 규칙(2026-07-14 확정):
 * <ol>
 *   <li>같은 시간대(시그니처)를 가진 요일을 한 줄로 묶는다 — <b>비연속 포함</b>(월·수가 같으면 화가 휴무여도 {@code 월 / 수}).</li>
 *   <li>줄 순서는 그룹의 <b>가장 이른 요일(월→일)</b> 기준 오름차순. 요일 나열도 월→일 순.</li>
 *   <li><b>영업하는 요일이 전부 같은 시간대</b>면 요일 나열 대신 {@code 매일}로 축약한다.</li>
 *   <li>휴무 요일이 있으면 <b>맨 아래</b> 한 줄로 묶는다({@code 토 / 일 휴무}). 전부 영업이면 휴무 줄 없음.</li>
 * </ol>
 * 시간은 24시간 {@code HH:mm ~ HH:mm}. 영업 요일이 하나도 없으면(정보 없음) {@code null}을 반환한다.
 */
@Component
public class OpeningHoursFormatter {

	private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

	/** 월→일 순회 순서(그룹 정렬·요일 나열의 기준). */
	private static final List<DayOfWeek> WEEK_ORDER = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
			DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

	private static final Map<DayOfWeek, String> LABEL = Map.of(
			DayOfWeek.MONDAY, "월", DayOfWeek.TUESDAY, "화", DayOfWeek.WEDNESDAY, "수", DayOfWeek.THURSDAY, "목",
			DayOfWeek.FRIDAY, "금", DayOfWeek.SATURDAY, "토", DayOfWeek.SUNDAY, "일");

	/**
	 * 표시 문자열(여러 줄은 {@code \n}으로 결합)로 변환한다. 영업 요일이 없으면 {@code null}.
	 */
	public String format(WeeklyOpeningHours hours) {
		if (hours == null || hours.hasNoOpenDay()) {
			return null;
		}
		// 월→일 순회 → 시그니처별 요일 그룹. computeIfAbsent가 최초 등장(=가장 이른 요일) 순으로 삽입돼 정렬이 자연히 맞는다.
		LinkedHashMap<String, List<DayOfWeek>> groups = new LinkedHashMap<>();
		List<DayOfWeek> closed = new java.util.ArrayList<>();
		for (DayOfWeek day : WEEK_ORDER) {
			List<WeeklyOpeningHours.TimeRange> ranges = hours.byDay().get(day);
			if (ranges == null || ranges.isEmpty()) {
				closed.add(day);
				continue;
			}
			groups.computeIfAbsent(signature(ranges), s -> new java.util.ArrayList<>()).add(day);
		}

		boolean singleOpenSignature = groups.size() == 1;
		List<String> lines = new java.util.ArrayList<>();
		for (Map.Entry<String, List<DayOfWeek>> group : groups.entrySet()) {
			String days = singleOpenSignature ? "매일" : joinDays(group.getValue());
			lines.add(days + " " + group.getKey());
		}
		if (!closed.isEmpty()) {
			lines.add(joinDays(closed) + " 휴무");
		}
		return String.join("\n", lines);
	}

	/** 하루의 영업 구간들을 표시 텍스트로(그룹 키 겸용). 예: {@code 10:00 ~ 18:00} / {@code 10:00 ~ 12:00, 13:00 ~ 18:00}. */
	private String signature(List<WeeklyOpeningHours.TimeRange> ranges) {
		return ranges.stream()
				.map(r -> HHMM.format(r.open()) + " ~ " + HHMM.format(r.close()))
				.collect(Collectors.joining(", "));
	}

	private String joinDays(List<DayOfWeek> days) {
		return days.stream().map(LABEL::get).collect(Collectors.joining(" / "));
	}
}
