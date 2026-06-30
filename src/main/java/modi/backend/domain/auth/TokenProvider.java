package modi.backend.domain.auth;

import java.util.Optional;

import modi.backend.domain.user.User;

/**
 * 자체 JWT 발급·검증 포트. 구현(jjwt 등)은 infra가 한다.
 * provider = 이번 로그인에 사용한 소셜 provider(유저는 여러 개 보유 가능).
 */
public interface TokenProvider {

	AuthTokens issue(User user, String provider);

	/** 서명·만료 검증 통과 시 클레임, 아니면 empty. */
	Optional<TokenClaims> parse(String token);

	long accessTtlSeconds();

	long refreshTtlSeconds();
}
