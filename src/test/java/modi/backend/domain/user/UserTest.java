package modi.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

	@Test
	@DisplayName("소셜 가입: nickname 없으면 기본값, 프로필 미완·연령대 UNSPECIFIED로 생성")
	void createFromSocial_기본값() {
		assertThat(User.createFromSocial(null).getNickname()).isEqualTo("사용자");
		assertThat(User.createFromSocial("  ").getNickname()).isEqualTo("사용자");
		assertThat(User.createFromSocial("진").getNickname()).isEqualTo("진");
		assertThat(User.createFromSocial("진").isProfileCompleted()).isFalse();
		assertThat(User.createFromSocial("진").getAgeGroup()).isEqualTo(AgeGroup.UNSPECIFIED);
	}
}
