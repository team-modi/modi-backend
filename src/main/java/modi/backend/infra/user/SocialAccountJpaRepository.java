package modi.backend.infra.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.user.SocialAccount;

public interface SocialAccountJpaRepository extends JpaRepository<SocialAccount, Long> {

	Optional<SocialAccount> findByProviderAndProviderUserIdAndDeletedAtIsNull(String provider, String providerUserId);

	Optional<SocialAccount> findByUserIdAndProviderAndDeletedAtIsNull(Long userId, String provider);

	/** 사용자의 소셜 계정 전부(관리자 상세의 이메일 표시용 — 이메일은 SocialAccount에 있음). */
	List<SocialAccount> findByUserIdAndDeletedAtIsNull(Long userId);

	/** 여러 유저의 특정 provider(예: phone) 계정 벌크 조회(관리자 목록의 전화번호 표시용). */
	List<SocialAccount> findByProviderAndUserIdInAndDeletedAtIsNull(String provider, List<Long> userIds);
}
