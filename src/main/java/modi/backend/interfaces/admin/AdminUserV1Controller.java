package modi.backend.interfaces.admin;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import modi.backend.application.admin.AdminUserFacade;
import modi.backend.application.admin.AdminUserResult;
import modi.backend.interfaces.record.dto.PageResponse;
import modi.backend.support.response.ApiResponse;

/**
 * 관리자 사용자 조회. `/api-admin/**` 게이트는 {@code AdminAuthInterceptor}가 담당.
 * 내부 콘솔이라 application Result를 응답으로 직접 반환한다(의도된 단순화).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/users")
public class AdminUserV1Controller implements AdminUserV1ApiSpec {

	private final AdminUserFacade adminUserFacade;

	@Override
	@GetMapping
	public ApiResponse<PageResponse<AdminUserResult.UserListItem>> list(
			@RequestParam(required = false) String q,
			@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.success(PageResponse.from(adminUserFacade.list(q, pageable)));
	}

	@Override
	@GetMapping("/{userId}")
	public ApiResponse<AdminUserResult.UserDetail> detail(@PathVariable Long userId) {
		return ApiResponse.success(adminUserFacade.detail(userId));
	}
}
