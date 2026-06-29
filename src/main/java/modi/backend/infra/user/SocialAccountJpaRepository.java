package modi.backend.infra.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.user.SocialAccount;

public interface SocialAccountJpaRepository extends JpaRepository<SocialAccount, Long> {

	Optional<SocialAccount> findByProviderAndProviderUserIdAndDeletedAtIsNull(String provider, String providerUserId);

	Optional<SocialAccount> findByUserIdAndProviderAndDeletedAtIsNull(Long userId, String provider);
}
