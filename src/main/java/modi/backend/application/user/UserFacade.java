package modi.backend.application.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.user.User;
import modi.backend.domain.user.UserErrorCode;
import modi.backend.domain.user.UserRepository;
import modi.backend.support.error.CoreException;

/**
 * 사용자 프로필 유스케이스 조율. 상태 변경은 Entity 메서드, Facade는 load·조율·save만.
 */
@Service
@RequiredArgsConstructor
public class UserFacade {

	private final UserRepository userRepository;

	/** 온보딩: nickname 보완 + profileCompleted=true. */
	@Transactional
	public UserResult.Profile completeProfile(UserCriteria.ProfileUpdate criteria) {
		User user = userRepository.findById(criteria.userId())
				.orElseThrow(() -> new CoreException(UserErrorCode.USER_NOT_FOUND));
		user.completeProfile(criteria.nickname());
		return UserResult.Profile.from(userRepository.save(user));
	}
}
