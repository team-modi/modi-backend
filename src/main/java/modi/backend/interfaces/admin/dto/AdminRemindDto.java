package modi.backend.interfaces.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 관리자 리마인드 설정 요청. */
public final class AdminRemindDto {

	private AdminRemindDto() {
	}

	/** 소환 대기 시간 변경(초). 1초 ~ 30일. */
	public record UpdateEligibleAfter(
			@Schema(description = "리마인드 소환 대기 시간(초). 예: 3(베타), 604800(정식 7일)", example = "3")
			@Min(1) @Max(2592000) long seconds) {
	}
}
