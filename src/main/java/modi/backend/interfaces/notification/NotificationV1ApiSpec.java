package modi.backend.interfaces.notification;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import modi.backend.domain.notification.NotificationType;
import modi.backend.interfaces.auth.LoginUser;
import modi.backend.interfaces.common.dto.CursorResponse;
import modi.backend.interfaces.notification.dto.NotificationDto;
import modi.backend.support.response.ApiResponse;

/**
 * 알림 API Swagger 스펙. (MVC 어노테이션은 {@link NotificationV1Controller})
 */
@Tag(name = "Notification", description = "알림 목록 조회 · 읽음 처리")
public interface NotificationV1ApiSpec {

	@Operation(summary = "알림 목록 조회",
			description = "내 알림을 최신순으로 조회한다(커서 페이지네이션). 조회 시 조건 충족 알림(오늘의 여운·전시 종료 임박)을 lazy 생성한다. access 토큰 필요.")
	@SecurityRequirement(name = "bearerAuth")
	ResponseEntity<ApiResponse<CursorResponse<NotificationDto.NotificationItem>>> getNotifications(
			@Parameter(hidden = true) LoginUser user,
			@Parameter(description = "알림 종류 필터(REMIND=오늘의 여운, EXHIBITION=전시, NOTICE=공지). 생략=전체") NotificationType type,
			@Parameter(description = "다음 페이지 조회용 opaque 커서(첫 페이지는 생략)") String cursor,
			@Parameter(description = "페이지 크기(기본 20, 최대 50)") Integer size);

	@Operation(summary = "알림 읽음 처리", description = "알림을 읽음으로 표시한다(멱등). 없거나 타인 알림이면 404. access 토큰 필요.")
	@SecurityRequirement(name = "bearerAuth")
	ResponseEntity<ApiResponse<NotificationDto.ReadResponse>> markRead(
			@Parameter(hidden = true) LoginUser user,
			@Parameter(description = "알림 id") Long notificationId);
}
