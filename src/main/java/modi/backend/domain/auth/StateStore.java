package modi.backend.domain.auth;

/**
 * OAuth state(CSRF 방어) 저장 포트. 구현은 infra(인메모리/Redis 등).
 */
public interface StateStore {

	void save(String state);

	/** 존재하고 만료 전이면 true(1회 소비). */
	boolean consume(String state);
}
