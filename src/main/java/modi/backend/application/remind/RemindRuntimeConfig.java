package modi.backend.application.remind;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import modi.backend.config.RemindProperties;

/**
 * 리마인드 소환 대기 시간의 런타임 오버라이드 홀더.
 * 시작값은 {@link RemindProperties#eligibleAfter()}(설정/env)이고, 관리자 API로 재배포 없이 즉시 바꿀 수 있다.
 * 프로세스 메모리에만 있으므로 재시작 시 설정 기본값으로 되돌아간다(베타 운영 노브 — 영속화 불필요).
 */
@Component
public class RemindRuntimeConfig {

	private final AtomicReference<Duration> eligibleAfter;

	public RemindRuntimeConfig(RemindProperties properties) {
		this.eligibleAfter = new AtomicReference<>(properties.eligibleAfter());
	}

	public Duration eligibleAfter() {
		return eligibleAfter.get();
	}

	public void updateEligibleAfter(Duration next) {
		eligibleAfter.set(next);
	}
}
