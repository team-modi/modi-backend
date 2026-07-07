package modi.backend.support.time;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 애플리케이션의 "업무상 오늘/지금"이 따르는 타임존(한국, KST).
 *
 * <p>JVM 기본 타임존은 UTC로 두고(=DB 서버와 정렬해 LocalDate가 DATE로 저장될 때 하루 밀리는 것을 방지),
 * 사용자에게 의미 있는 "오늘"(관람일 기본값·진행중 전시 필터 등)은 이 KST 존을 명시적으로 사용한다.
 * 문자열 리터럴("Asia/Seoul")이 코드 곳곳에 흩어지지 않도록 단일 소스로 둔다.
 */
public final class AppTime {

	public static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private AppTime() {
	}

	/** KST 기준 시스템 시계. LocalDate.now(clock) 등 "한국 기준 오늘"이 필요한 곳에 쓴다. */
	public static Clock clock() {
		return Clock.system(KST);
	}
}
