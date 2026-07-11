package modi.backend.application.activitylog;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import modi.backend.domain.activitylog.ActivityLog;
import modi.backend.infra.activitylog.ActivityLogJpaRepository;

/**
 * 활동 로그 기록. 인터셉터가 요청 처리 후 호출하며, 별도 실행기에서 비동기로 저장한다.
 * best-effort — 저장 실패가 요청/앱에 영향을 주지 않도록 예외를 삼킨다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityLogService {

	private final ActivityLogJpaRepository activityLogRepository;

	@Async("activityLogExecutor")
	public void record(Long userId, String method, String path, int status, long durationMs) {
		try {
			activityLogRepository.save(ActivityLog.create(userId, method, path, status, durationMs));
		} catch (Exception e) {
			// 활동 로그는 부가 기능 — 실패해도 조용히 넘어간다(요청은 이미 끝났고, 앱 동작엔 영향 없음).
			log.debug("활동 로그 기록 실패 (무시): {} {} - {}", method, path, e.toString());
		}
	}
}
