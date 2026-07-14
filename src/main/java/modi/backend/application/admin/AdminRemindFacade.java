package modi.backend.application.admin;

import java.time.Duration;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import modi.backend.application.remind.RemindRuntimeConfig;
import modi.backend.domain.remind.RemindErrorCode;
import modi.backend.support.error.CoreException;

/**
 * 관리자 리마인드 설정 유스케이스 — 소환 대기 시간(eligibleAfter)을 재배포 없이 런타임에 조회/변경.
 * 값은 {@link RemindRuntimeConfig}(프로세스 메모리)에 반영되므로 즉시 적용되고, 재시작 시 설정 기본값으로 복귀한다.
 */
@Service
@RequiredArgsConstructor
public class AdminRemindFacade {

	/** 허용 범위: 1초 ~ 30일. 0/음수·비현실적 값 방지(정식 7d, 베타 3s 등을 모두 포함). */
	private static final long MIN_SECONDS = 1L;
	private static final long MAX_SECONDS = 30L * 24 * 60 * 60; // 30일

	private final RemindRuntimeConfig remindRuntimeConfig;

	public AdminRemindResult.EligibleAfter getEligibleAfter() {
		return toResult(remindRuntimeConfig.eligibleAfter());
	}

	public AdminRemindResult.EligibleAfter updateEligibleAfter(long seconds) {
		if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) {
			throw new CoreException(RemindErrorCode.INVALID_REMIND_INPUT);
		}
		remindRuntimeConfig.updateEligibleAfter(Duration.ofSeconds(seconds));
		return toResult(remindRuntimeConfig.eligibleAfter());
	}

	private AdminRemindResult.EligibleAfter toResult(Duration d) {
		return new AdminRemindResult.EligibleAfter(d.getSeconds(), humanize(d.getSeconds()));
	}

	// 정확히 나누어떨어지는 가장 큰 단위로 표기. 아니면 초로.
	private String humanize(long seconds) {
		if (seconds % 86400 == 0) {
			return (seconds / 86400) + "일";
		}
		if (seconds % 3600 == 0) {
			return (seconds / 3600) + "시간";
		}
		if (seconds % 60 == 0) {
			return (seconds / 60) + "분";
		}
		return seconds + "초";
	}
}
