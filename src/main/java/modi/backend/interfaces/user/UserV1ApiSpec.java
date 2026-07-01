package modi.backend.interfaces.user;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import modi.backend.interfaces.auth.LoginUser;
import modi.backend.interfaces.user.dto.UserDto;
import modi.backend.support.response.ApiResponse;

/**
 * 사용자 API Swagger 스펙. (MVC 어노테이션은 {@link UserV1Controller})
 */
@Tag(name = "User", description = "프로필 조회 · 수정")
public interface UserV1ApiSpec {

	@Operation(summary = "내 프로필 조회", description = "프로필 + 취향 키워드 + 활동 통계. access 토큰 필요.")
	@SecurityRequirement(name = "bearerAuth")
	ResponseEntity<ApiResponse<UserDto.MeResponse>> me(@Parameter(hidden = true) LoginUser user);

	@Operation(summary = "내 프로필 수정", description = "전달된 필드만 부분 갱신(닉네임·프로필이미지·연령대·거주지역). access 토큰 필요.")
	@SecurityRequirement(name = "bearerAuth")
	ResponseEntity<ApiResponse<UserDto.ProfileResponse>> updateProfile(
			@Parameter(hidden = true) LoginUser user,
			UserDto.ProfileRequest request);
}
