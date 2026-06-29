package modi.backend.infra.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import modi.backend.domain.auth.StateStore;

/**
 * {@link StateStore} 인메모리 구현(5분 TTL, 1회 소비). 운영은 Redis 등으로 교체.
 */
@Component
public class InMemoryStateStore implements StateStore {

	private static final Duration TTL = Duration.ofMinutes(5);
	private final Map<String, Instant> states = new ConcurrentHashMap<>();

	@Override
	public void save(String state) {
		states.put(state, Instant.now().plus(TTL));
	}

	@Override
	public boolean consume(String state) {
		Instant expireAt = states.remove(state);
		return expireAt != null && expireAt.isAfter(Instant.now());
	}
}
