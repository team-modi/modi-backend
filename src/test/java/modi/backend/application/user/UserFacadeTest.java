package modi.backend.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.user.User;
import modi.backend.domain.user.UserRepository;
import modi.backend.support.error.CoreException;

/**
 * 온보딩 유스케이스 통합 검증. 실제 저장소(Testcontainers MySQL)로 load→행위→save 흐름을 확인한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserFacadeTest {

	@Autowired
	UserFacade userFacade;

	@Autowired
	UserRepository userRepository;

	@Test
	@DisplayName("completeProfile: nickname 보완 + profileCompleted=true로 영속화")
	void completeProfile_영속화() {
		User saved = userRepository.save(User.createFromSocial("초기"));

		UserResult.Profile result = userFacade.completeProfile(new UserCriteria.ProfileUpdate(saved.getId(), "완성닉"));

		assertThat(result.profileCompleted()).isTrue();
		assertThat(result.nickname()).isEqualTo("완성닉");
		User reloaded = userRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.isProfileCompleted()).isTrue();
		assertThat(reloaded.getNickname()).isEqualTo("완성닉");
	}

	@Test
	@DisplayName("completeProfile: 없는 유저면 USER_NOT_FOUND")
	void completeProfile_없는유저() {
		assertThatThrownBy(() -> userFacade.completeProfile(new UserCriteria.ProfileUpdate(999999L, "x")))
				.isInstanceOf(CoreException.class);
	}
}
