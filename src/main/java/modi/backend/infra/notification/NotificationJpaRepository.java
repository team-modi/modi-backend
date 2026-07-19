package modi.backend.infra.notification;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import modi.backend.domain.notification.Notification;
import modi.backend.domain.notification.NotificationType;

/**
 * Spring Data JPA. 키셋 페이지네이션은 @Query + Pageable(limit)로 구성한다(프로젝트에 QueryDSL 미도입).
 * 조회는 soft-delete 되지 않은 행만 본다.
 */
public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {

	Optional<Notification> findByIdAndDeletedAtIsNull(Long id);

	long countByUserIdAndDeletedAtIsNull(Long userId);

	long countByUserIdAndTypeAndDeletedAtIsNull(Long userId, NotificationType type);

	/** 같은 (사용자, 종류, 대상) 알림 존재 여부 — lazy 생성의 멱등 판정용(살아있는 행만). */
	boolean existsByUserIdAndTypeAndTargetIdAndDeletedAtIsNull(Long userId, NotificationType type, Long targetId);

	/** 첫 페이지 — 최신순(createdAt desc, id desc). type=null이면 전체 종류. */
	@Query("select n from Notification n where n.userId = :userId and n.deletedAt is null "
			+ "and (:type is null or n.type = :type) "
			+ "order by n.createdAt desc, n.id desc")
	List<Notification> findFirstPage(@Param("userId") Long userId,
			@Param("type") NotificationType type,
			Pageable pageable);

	/** 커서 이후 페이지 — (createdAt < :cAt) OR (createdAt = :cAt AND id < :cId), 최신순. type=null이면 전체 종류. */
	@Query("select n from Notification n where n.userId = :userId and n.deletedAt is null "
			+ "and (:type is null or n.type = :type) "
			+ "and (n.createdAt < :cursorCreatedAt "
			+ "or (n.createdAt = :cursorCreatedAt and n.id < :cursorId)) "
			+ "order by n.createdAt desc, n.id desc")
	List<Notification> findPageAfterCursor(@Param("userId") Long userId,
			@Param("type") NotificationType type,
			@Param("cursorCreatedAt") ZonedDateTime cursorCreatedAt,
			@Param("cursorId") Long cursorId,
			Pageable pageable);

	/** 탈퇴 cascade용: 사용자의 살아있는 알림 전부. */
	List<Notification> findByUserIdAndDeletedAtIsNull(Long userId);
}
