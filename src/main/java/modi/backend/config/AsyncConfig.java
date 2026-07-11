package modi.backend.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 실행기. 활동 로그 기록을 요청 스레드에서 떼어 낸다(요청 지연·앱 보호).
 * 1GB 박스라 스레드/큐를 작게 잡고, 넘치면 오래된 작업을 버려(로그 유실 감수) 절대 요청을 막지 않는다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean("activityLogExecutor")
	public Executor activityLogExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(500);
		executor.setThreadNamePrefix("activity-log-");
		// 큐가 가득 차면 가장 오래된 대기 작업을 버린다 — 요청 스레드가 블로킹되지 않게(best-effort 로그).
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
		executor.initialize();
		return executor;
	}
}
