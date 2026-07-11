package modi.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 리마인드(오늘의 여운) 설정. {@code app.remind.*} 바인딩.
 *
 * @param eligibleAfter 기록 작성 후 소환 대상이 되기까지의 경과 시간. 정식 기본 7d.
 *                      베타처럼 짧은 기간에 리마인드를 검증해야 하면 env(REMIND_ELIGIBLE_AFTER)로 1m 등으로 단축한다
 *                      — 리마인드는 스케줄 발송이 아니라 조회 시점 판정(pull)이라 이 값만 줄이면 즉시 반영된다.
 */
@ConfigurationProperties(prefix = "app.remind")
public record RemindProperties(Duration eligibleAfter) {

	public RemindProperties {
		if (eligibleAfter == null || eligibleAfter.isNegative() || eligibleAfter.isZero()) {
			eligibleAfter = Duration.ofDays(7);
		}
	}
}
