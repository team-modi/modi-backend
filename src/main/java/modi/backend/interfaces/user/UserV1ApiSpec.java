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
@Tag(name = "User", description = "온보딩 · 프로필")
public interface UserV1ApiSpec {

	@Operation(summary = "내 프로필 완료(온보딩)", description = "nickname 보완 후 profileCompleted=true. access 토큰 필요.")
	@SecurityRequirement(name = "bearerAuth")
	ResponseEntity<ApiResponse<UserDto.ProfileResponse>> updateProfile(
			@Parameter(hidden = true) LoginUser user,
			UserDto.ProfileRequest request);
}
