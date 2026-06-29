package modi.backend.domain.user;

import java.util.Optional;

/**
 * User 영속화 포트(도메인 소유). 구현은 infra(DIP).
 */
public interface UserRepository {

	Optional<User> findById(Long id);

	User save(User user);
}
