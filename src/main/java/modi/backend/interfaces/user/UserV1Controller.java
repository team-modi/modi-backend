package modi.backend.interfaces.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import modi.backend.application.user.UserCriteria;
import modi.backend.application.user.UserFacade;
import modi.backend.application.user.UserWithdrawalFacade;
import modi.backend.application.user.UserResult;
import modi.backend.interfaces.auth.Authentication;
import modi.backend.interfaces.auth.LoginUser;
import modi.backend.interfaces.user.dto.UserDto;
import modi.backend.support.response.ApiResponse;

/**
 * FE 주도 사용자 API. 인증은 access 토큰(쿠키/Bearer)으로, 대상 유저는 @Authentication으로 주입받는다.
 * (프로젝트 컨벤션: 성공 200, 프로필 수정은 PUT — 전달된 필드만 부분 갱신.)
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserV1Controller implements UserV1ApiSpec {

	private final UserFacade userFacade;
	private final UserWithdrawalFacade userWithdrawalFacade;

	/** 내 프로필 + 취향 키워드 + 활동 통계 조회. */
	@Override
	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserDto.MeResponse>> me(@Authentication LoginUser user) {
		UserResult.Me result = userFacade.getProfile(new UserCriteria.Me(user.userId(), user.provider()));
		return ResponseEntity.ok(ApiResponse.success(UserDto.MeResponse.from(result)));
	}

	/** 프로필 수정(부분 갱신). 닉네임·연령대·거주지역은 도메인에서 검증. */
	@Override
	@PutMapping("/me/profile")
	public ResponseEntity<ApiResponse<UserDto.ProfileResponse>> updateProfile(
			@Authentication LoginUser user,
			@Valid @RequestBody UserDto.ProfileRequest request) {
		UserResult.Profile result = userFacade.updateProfile(new UserCriteria.ProfileUpdate(
				user.userId(), user.provider(), request.nickname(), request.profileImageUrl(),
				request.ageGroup(), request.residenceRegion(), request.residenceDistrict()));
		return ResponseEntity.ok(ApiResponse.success(UserDto.ProfileResponse.from(result)));
	}

	/** 알림 설정 조회. */
	@Override
	@GetMapping("/me/notification-settings")
	public ResponseEntity<ApiResponse<UserDto.NotificationSettingsResponse>> notificationSettings(
			@Authentication LoginUser user) {
		UserResult.NotificationSettings result = userFacade.getNotificationSettings(
				new UserCriteria.Me(user.userId(), user.provider()));
		return ResponseEntity.ok(ApiResponse.success(UserDto.NotificationSettingsResponse.from(result)));
	}

	/** 알림 설정 수정(리마인드·공지 수신 여부 전체 갱신). */
	@Override
	@PutMapping("/me/notification-settings")
	public ResponseEntity<ApiResponse<UserDto.NotificationSettingsResponse>> updateNotificationSettings(
			@Authentication LoginUser user,
			@Valid @RequestBody UserDto.NotificationSettingsRequest request) {
		UserResult.NotificationSettings result = userFacade.updateNotificationSettings(
				new UserCriteria.NotificationUpdate(user.userId(), request.remindEnabled(), request.noticeEnabled()));
		return ResponseEntity.ok(ApiResponse.success(UserDto.NotificationSettingsResponse.from(result)));
	}

	/** 회원 탈퇴(soft-delete + 토큰 무효화). 응답 data는 null. */
	@Override
	@DeleteMapping("/me")
	public ResponseEntity<ApiResponse<Object>> withdraw(@Authentication LoginUser user) {
		userWithdrawalFacade.withdraw(user.userId());
		return ResponseEntity.ok(ApiResponse.success());
	}
}
