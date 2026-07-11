package modi.backend.interfaces.admin;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import modi.backend.application.admin.AdminUserResult;
import modi.backend.interfaces.record.dto.PageResponse;
import modi.backend.support.response.ApiResponse;

@Tag(name = "Admin - Users", description = "관리자 사용자 조회(목록/상세). 관리자 화이트리스트만 접근.")
@SecurityRequirement(name = "bearerAuth")
public interface AdminUserV1ApiSpec {

	@Operation(summary = "사용자 목록", description = "닉네임/실명 검색 + 사용자별 기록·리마인드·북마크·API호출 수, 마지막 활동. 최신 가입순.")
	ApiResponse<PageResponse<AdminUserResult.UserListItem>> list(
			@Parameter(description = "닉네임/실명 부분검색") String q,
			@ParameterObject Pageable pageable);

	@Operation(summary = "사용자 상세", description = "프로필 + 총계 + 최근 기록/리마인드/북마크/전시활동 + API 호출 활동 기록.")
	ApiResponse<AdminUserResult.UserDetail> detail(
			@Parameter(description = "사용자 ID") Long userId);
}
