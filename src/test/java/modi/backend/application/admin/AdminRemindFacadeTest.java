package modi.backend.application.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import modi.backend.application.remind.RemindRuntimeConfig;
import modi.backend.config.RemindProperties;
import modi.backend.support.error.CoreException;

class AdminRemindFacadeTest {

	private AdminRemindFacade facade(Duration initial) {
		RemindRuntimeConfig runtime = new RemindRuntimeConfig(new RemindProperties(initial));
		return new AdminRemindFacade(runtime);
	}

	@Test
	@DisplayName("조회_시작값_초와라벨을_반환한다")
	void get_returnsInitial() {
		AdminRemindResult.EligibleAfter result = facade(Duration.ofSeconds(3)).getEligibleAfter();

		assertThat(result.seconds()).isEqualTo(3);
		assertThat(result.label()).isEqualTo("3초");
	}

	@Test
	@DisplayName("변경_유효한값이면_즉시반영되고_라벨을_사람이읽게_표기한다")
	void update_validSeconds_appliesAndHumanizes() {
		AdminRemindFacade facade = facade(Duration.ofSeconds(3));

		assertThat(facade.updateEligibleAfter(60).label()).isEqualTo("1분");
		assertThat(facade.updateEligibleAfter(3600).label()).isEqualTo("1시간");
		assertThat(facade.updateEligibleAfter(604800).label()).isEqualTo("7일");
		// 마지막 변경값이 조회에 그대로 반영
		assertThat(facade.getEligibleAfter().seconds()).isEqualTo(604800);
	}

	@Test
	@DisplayName("변경_0이하면_INVALID_예외")
	void update_zeroOrNegative_throws() {
		AdminRemindFacade facade = facade(Duration.ofSeconds(3));

		assertThatThrownBy(() -> facade.updateEligibleAfter(0)).isInstanceOf(CoreException.class);
		assertThatThrownBy(() -> facade.updateEligibleAfter(-5)).isInstanceOf(CoreException.class);
	}

	@Test
	@DisplayName("변경_상한(30일)초과면_INVALID_예외")
	void update_overMax_throws() {
		AdminRemindFacade facade = facade(Duration.ofSeconds(3));

		assertThatThrownBy(() -> facade.updateEligibleAfter(30L * 24 * 60 * 60 + 1))
				.isInstanceOf(CoreException.class);
	}
}
