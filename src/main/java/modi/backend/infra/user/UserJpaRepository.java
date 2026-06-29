package modi.backend.infra.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.user.User;

public interface UserJpaRepository extends JpaRepository<User, Long> {

	/** soft delete된 행은 제외하고 조회. */
	Optional<User> findByIdAndDeletedAtIsNull(Long id);
}
