package modi.backend.interfaces.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import modi.backend.application.admin.AdminRemindFacade;
import modi.backend.application.admin.AdminRemindResult;
import modi.backend.interfaces.admin.dto.AdminRemindDto;
import modi.backend.support.response.ApiResponse;

/**
 * 관리자 리마인드 설정. `/api-admin/**` 는 {@code AdminAuthInterceptor}가 관리자만 통과시키므로 컨트롤러는 조율만 한다.
 * 내부 콘솔이라 application Result를 응답으로 직접 반환한다(별도 Response DTO 생략 — 의도된 단순화).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/reminds")
public class AdminRemindV1Controller implements AdminRemindV1ApiSpec {

	private final AdminRemindFacade adminRemindFacade;

	@Override
	@GetMapping("/eligible-after")
	public ApiResponse<AdminRemindResult.EligibleAfter> getEligibleAfter() {
		return ApiResponse.success(adminRemindFacade.getEligibleAfter());
	}

	@Override
	@PutMapping("/eligible-after")
	public ApiResponse<AdminRemindResult.EligibleAfter> updateEligibleAfter(
			@RequestBody @Valid AdminRemindDto.UpdateEligibleAfter request) {
		return ApiResponse.success(adminRemindFacade.updateEligibleAfter(request.seconds()));
	}
}
