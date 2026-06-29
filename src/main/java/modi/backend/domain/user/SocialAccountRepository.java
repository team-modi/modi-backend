package modi.backend.domain.user;

import java.util.Optional;

/**
 * SocialAccount 영속화 포트(도메인 소유). 구현은 infra(DIP).
 */
public interface SocialAccountRepository {

	Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

	Optional<SocialAccount> findByUserIdAndProvider(Long userId, String provider);

	SocialAccount save(SocialAccount socialAccount);
}
