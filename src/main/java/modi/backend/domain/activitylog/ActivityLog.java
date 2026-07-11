package modi.backend.domain.activitylog;

import static lombok.AccessLevel.PROTECTED;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * API 활동 로그 — 인증된 요청 1건 = 1행. 관리자 콘솔의 사용자별 활동/호출 집계에 쓰인다.
 * append-only(수정·소프트삭제 없음). 기록은 인터셉터가 비동기로 수행한다.
 */
@Entity
@Table(name = "activity_logs")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class ActivityLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 10)
	private String method;

	// 쿼리스트링 제외한 경로만(길이 제한·개인정보 최소화). 원본 URI가 255를 넘으면 잘라 저장한다.
	@Column(nullable = false, length = 255)
	private String path;

	@Column(nullable = false)
	private int status;

	@Column(name = "duration_ms", nullable = false)
	private long durationMs;

	@Column(name = "created_at", nullable = false, updatable = false)
	private ZonedDateTime createdAt;

	private ActivityLog(Long userId, String method, String path, int status, long durationMs) {
		this.userId = userId;
		this.method = method;
		this.path = path;
		this.status = status;
		this.durationMs = durationMs;
	}

	public static ActivityLog create(Long userId, String method, String path, int status, long durationMs) {
		String safePath = path == null ? "" : (path.length() > 255 ? path.substring(0, 255) : path);
		return new ActivityLog(userId, method, safePath, status, durationMs);
	}

	@PrePersist
	private void prePersist() {
		this.createdAt = ZonedDateTime.now();
	}
}
