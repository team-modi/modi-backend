package modi.backend.domain.auth;

/**
 * refresh 토큰 저장 포트(회전 지원). 구현은 infra(인메모리/Redis/DB 등).
 */
public interface RefreshTokenStore {

	void save(Long userId, String refreshToken);

	boolean matches(Long userId, String refreshToken);

	void remove(Long userId);
}
