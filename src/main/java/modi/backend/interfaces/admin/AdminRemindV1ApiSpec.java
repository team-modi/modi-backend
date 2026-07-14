package modi.backend.interfaces.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import modi.backend.application.admin.AdminRemindResult;
import modi.backend.interfaces.admin.dto.AdminRemindDto;
import modi.backend.support.response.ApiResponse;

@Tag(name = "Admin - Remind", description = "관리자 리마인드 설정. 소환 대기 시간을 재배포 없이 런타임에 조정(관리자만 접근).")
@SecurityRequirement(name = "bearerAuth")
public interface AdminRemindV1ApiSpec {

	@Operation(summary = "소환 대기 시간 조회", description = "현재 리마인드 소환 대기 시간(초 + 사람이 읽는 표기).")
	ApiResponse<AdminRemindResult.EligibleAfter> getEligibleAfter();

	@Operation(summary = "소환 대기 시간 변경",
			description = "기록 작성 후 리마인드 후보가 되기까지의 경과 시간을 즉시 변경(프로세스 메모리 — 재시작 시 설정 기본값 복귀).")
	ApiResponse<AdminRemindResult.EligibleAfter> updateEligibleAfter(
			@Valid AdminRemindDto.UpdateEligibleAfter request);
}
