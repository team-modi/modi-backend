package modi.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

	@Test
	@DisplayName("소셜 가입: nickname 없으면 기본값, 프로필 미완으로 생성")
	void createFromSocial_기본값() {
		assertThat(User.createFromSocial(null).getNickname()).isEqualTo("사용자");
		assertThat(User.createFromSocial("  ").getNickname()).isEqualTo("사용자");
		assertThat(User.createFromSocial("진").getNickname()).isEqualTo("진");
		assertThat(User.createFromSocial("진").isProfileCompleted()).isFalse();
	}

	@Test
	@DisplayName("온보딩: nickname 보완 + profileCompleted=true")
	void completeProfile_완료처리() {
		User user = User.createFromSocial("기존");

		user.completeProfile("새닉네임");

		assertThat(user.getNickname()).isEqualTo("새닉네임");
		assertThat(user.isProfileCompleted()).isTrue();
	}

	@Test
	@DisplayName("온보딩: 빈 nickname이면 기존 유지하되 완료 처리")
	void completeProfile_빈값_기존유지() {
		User user = User.createFromSocial("기존");

		user.completeProfile("  ");

		assertThat(user.getNickname()).isEqualTo("기존");
		assertThat(user.isProfileCompleted()).isTrue();
	}
}
