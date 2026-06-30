package modi.backend.infra.user;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.user.SocialAccount;
import modi.backend.domain.user.SocialAccountRepository;

/**
 * {@link SocialAccountRepository} 어댑터(DIP). Spring Data로 위임.
 */
@Repository
@RequiredArgsConstructor
public class SocialAccountRepositoryImpl implements SocialAccountRepository {

	private final SocialAccountJpaRepository jpaRepository;

	@Override
	public Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId) {
		return jpaRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(provider, providerUserId);
	}

	@Override
	public Optional<SocialAccount> findByUserIdAndProvider(Long userId, String provider) {
		return jpaRepository.findByUserIdAndProviderAndDeletedAtIsNull(userId, provider);
	}

	@Override
	public SocialAccount save(SocialAccount socialAccount) {
		return jpaRepository.save(socialAccount);
	}
}
