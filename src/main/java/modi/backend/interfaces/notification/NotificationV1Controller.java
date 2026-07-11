package modi.backend.interfaces.notification;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import modi.backend.application.notification.NotificationCriteria;
import modi.backend.application.notification.NotificationFacade;
import modi.backend.application.notification.NotificationResult;
import modi.backend.domain.notification.NotificationType;
import modi.backend.interfaces.auth.Authentication;
import modi.backend.interfaces.auth.LoginUser;
import modi.backend.interfaces.common.dto.CursorResponse;
import modi.backend.interfaces.notification.dto.NotificationDto;
import modi.backend.support.response.ApiResponse;

/**
 * FE 주도 알림 API(09_알림.md). 인증은 access 토큰으로, 대상 유저는 @Authentication으로 주입받는다.
 * (프로젝트 컨벤션: 성공 200, 목록은 커서 페이지네이션.)
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationV1Controller implements NotificationV1ApiSpec {

	private final NotificationFacade notificationFacade;

	/** 내 알림 목록(최신순, 커서 페이지네이션). 조회 전에 lazy 생성(refresh)으로 조건 충족 알림을 채워 넣는다. */
	@Override
	@GetMapping
	public ResponseEntity<ApiResponse<CursorResponse<NotificationDto.NotificationItem>>> getNotifications(
			@Authentication LoginUser user,
			@RequestParam(required = false) NotificationType type,
			@RequestParam(required = false) String cursor,
			@RequestParam(required = false) Integer size) {
		notificationFacade.refresh(user.userId());
		NotificationResult.List result = notificationFacade.getNotifications(
				new NotificationCriteria.List(user.userId(), type, cursor, size));
		return ResponseEntity.ok(ApiResponse.success(NotificationDto.toCursorResponse(result)));
	}

	/** 알림 읽음 처리(멱등). */
	@Override
	@PutMapping("/{notificationId}/read")
	public ResponseEntity<ApiResponse<NotificationDto.ReadResponse>> markRead(
			@Authentication LoginUser user,
			@PathVariable Long notificationId) {
		NotificationResult.Read result = notificationFacade.markRead(
				new NotificationCriteria.Read(user.userId(), notificationId));
		return ResponseEntity.ok(ApiResponse.success(NotificationDto.ReadResponse.from(result)));
	}
}
