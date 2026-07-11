package modi.backend.domain.notification;

/**
 * 알림 종류.
 * REMIND=오늘의 여운 도착(targetId=recordId) · EXHIBITION=북마크 전시 종료 임박(targetId=exhibitionId)
 * · NOTICE=공지(targetId=null).
 */
public enum NotificationType {
	REMIND,
	EXHIBITION,
	NOTICE
}
