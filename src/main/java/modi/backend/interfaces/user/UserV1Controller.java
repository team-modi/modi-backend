package modi.backend.interfaces.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import modi.backend.application.user.UserCriteria;
import modi.backend.application.user.UserFacade;
import modi.backend.application.user.UserResult;
import modi.backend.interfaces.auth.Authentication;
import modi.backend.interfaces.auth.LoginUser;
import modi.backend.interfaces.user.dto.UserDto;
import modi.backend.support.response.ApiResponse;

/**
 * FE 주도 사용자 API. 인증은 access 토큰(Bearer)으로, 대상 유저는 @Authentication으로 주입받는다.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserV1Controller implements UserV1ApiSpec {

	private final UserFacade userFacade;

	/** 온보딩: 내 프로필 입력 완료. (성공 200, PATCH 미사용 — PUT) */
	@Override
	@PutMapping("/me/profile")
	public ResponseEntity<ApiResponse<UserDto.ProfileResponse>> updateProfile(
			@Authentication LoginUser user,
			@Valid @RequestBody UserDto.ProfileRequest request) {
		UserResult.Profile result = userFacade.completeProfile(
				new UserCriteria.ProfileUpdate(user.userId(), request.nickname()));
		return ResponseEntity.ok(ApiResponse.success(UserDto.ProfileResponse.from(result)));
	}
}
