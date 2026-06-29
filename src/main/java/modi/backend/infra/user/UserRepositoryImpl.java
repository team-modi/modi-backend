package modi.backend.infra.user;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.user.User;
import modi.backend.domain.user.UserRepository;

/**
 * {@link UserRepository} 어댑터(DIP). Spring Data로 위임.
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

	private final UserJpaRepository jpaRepository;

	@Override
	public Optional<User> findById(Long id) {
		return jpaRepository.findByIdAndDeletedAtIsNull(id);
	}

	@Override
	public User save(User user) {
		return jpaRepository.save(user);
	}
}
