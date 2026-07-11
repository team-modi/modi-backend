package modi.backend.interfaces.notification.dto;

import java.time.ZonedDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import modi.backend.application.notification.NotificationResult;
import modi.backend.interfaces.common.dto.CursorResponse;

/**
 * 알림 API의 요청/응답 DTO 모음. (파일 수 절감을 위해 중첩 record로 묶음)
 */
public final class NotificationDto {

	private NotificationDto() {
	}

	/** 알림 목록 항목(9.1). type: REMIND(targetId=recordId) | EXHIBITION(targetId=exhibitionId) | NOTICE(targetId=null). */
	public record NotificationItem(
			Long notificationId,
			@Schema(description = "알림 종류", allowableValues = {"REMIND", "EXHIBITION", "NOTICE"}) String type,
			String title,
			String body,
			@Schema(description = "이동 대상 id. REMIND=recordId, EXHIBITION=exhibitionId, NOTICE=null",
					nullable = true) Long targetId,
			@Schema(description = "카드 썸네일 이미지 URL(전시 포스터). 없으면 null", nullable = true) String imageUrl,
			boolean read,
			ZonedDateTime createdAt) {

		public static NotificationItem from(NotificationResult.Item item) {
			return new NotificationItem(item.notificationId(), item.type(), item.title(), item.body(),
					item.targetId(), item.imageUrl(), item.read(), item.createdAt());
		}
	}

	/** 알림 목록 응답 — 커서 페이지네이션 봉투. */
	public static CursorResponse<NotificationItem> toCursorResponse(NotificationResult.List result) {
		return CursorResponse.of(
				result.content().stream().map(NotificationItem::from).toList(),
				result.nextCursor(), result.hasNext(), result.totalCount());
	}

	/** 읽음 처리 응답(9.2). read는 항상 true. */
	public record ReadResponse(Long notificationId, boolean read) {

		public static ReadResponse from(NotificationResult.Read result) {
			return new ReadResponse(result.notificationId(), result.read());
		}
	}
}
