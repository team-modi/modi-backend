package modi.backend.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.user.AgeGroup;
import modi.backend.domain.user.ResidenceRegion;
import modi.backend.domain.user.User;
import modi.backend.domain.user.UserRepository;
import modi.backend.support.error.CoreException;

/**
 * 프로필 수정 유스케이스 통합 검증. 실제 저장소(Testcontainers MySQL)로 load→행위→save 흐름을 확인한다.
 * (컨트롤러 경계에서 닿기 어려운 USER_NOT_FOUND·부분 갱신 영속화를 Facade 시임에서 커버.)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserFacadeTest {

	@Autowired
	UserFacade userFacade;

	@Autowired
	UserRepository userRepository;

	@Test
	@DisplayName("updateProfile: 전달된 필드만 갱신 + profileCompleted=true로 영속화")
	void updateProfile_영속화() {
		User saved = userRepository.save(User.createFromSocial("초기"));

		UserResult.Profile result = userFacade.updateProfile(new UserCriteria.ProfileUpdate(
				saved.getId(), "kakao", "완성닉", null, "TWENTIES", "SEOUL", "강남구"));

		assertThat(result.profileCompleted()).isTrue();
		assertThat(result.nickname()).isEqualTo("완성닉");
		assertThat(result.ageGroup()).isEqualTo("TWENTIES");
		assertThat(result.residenceRegion()).isEqualTo("SEOUL");
		User reloaded = userRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.isProfileCompleted()).isTrue();
		assertThat(reloaded.getNickname()).isEqualTo("완성닉");
		assertThat(reloaded.getAgeGroup()).isEqualTo(AgeGroup.TWENTIES);
		assertThat(reloaded.getResidenceRegion()).isEqualTo(ResidenceRegion.SEOUL);
	}

	@Test
	@DisplayName("updateProfile: 없는 유저면 USER_NOT_FOUND")
	void updateProfile_없는유저() {
		assertThatThrownBy(() -> userFacade.updateProfile(
				new UserCriteria.ProfileUpdate(999999L, "kakao", "x", null, null, null, null)))
				.isInstanceOf(CoreException.class);
	}
}
