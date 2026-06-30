package modi.backend.infra.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import modi.backend.domain.auth.RefreshTokenStore;

/**
 * {@link RefreshTokenStore} 인메모리 구현(userId → 현재 유효 refresh 1개, 회전).
 * 운영은 Redis/DB로 교체.
 */
@Component
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

	private final Map<Long, String> tokens = new ConcurrentHashMap<>();

	@Override
	public void save(Long userId, String refreshToken) {
		tokens.put(userId, refreshToken);
	}

	@Override
	public boolean matches(Long userId, String refreshToken) {
		return refreshToken.equals(tokens.get(userId));
	}

	@Override
	public void remove(Long userId) {
		tokens.remove(userId);
	}
}
