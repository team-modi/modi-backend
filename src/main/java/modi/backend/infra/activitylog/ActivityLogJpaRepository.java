package modi.backend.infra.activitylog;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import modi.backend.domain.activitylog.ActivityLog;

/**
 * 활동 로그 조회/집계. 관리자 콘솔 전용(읽기) + 인터셉터의 기록(save).
 * QueryDSL 미도입 프로젝트라 집계는 JPQL {@code @Query}로 작성한다.
 */
public interface ActivityLogJpaRepository extends JpaRepository<ActivityLog, Long> {

	// 사용자 상세: 최근 활동 트레일(페이지)
	Page<ActivityLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	// 사용자 상세: 총 호출 수
	long countByUserId(Long userId);

	// 사용자 상세: 엔드포인트별 호출 수(많은 순). Object[]{path, count}
	@Query("select a.path, count(a) from ActivityLog a where a.userId = :userId group by a.path order by count(a) desc")
	List<Object[]> countByPathForUser(@Param("userId") Long userId);

	// 사용자 목록(벌크): 유저ID들의 호출 수. Object[]{userId, count}
	@Query("select a.userId, count(a) from ActivityLog a where a.userId in :userIds group by a.userId")
	List<Object[]> countByUserIds(@Param("userIds") List<Long> userIds);

	// 사용자 목록(벌크): 유저ID들의 마지막 활동 시각. Object[]{userId, max(createdAt)}
	@Query("select a.userId, max(a.createdAt) from ActivityLog a where a.userId in :userIds group by a.userId")
	List<Object[]> lastActivityByUserIds(@Param("userIds") List<Long> userIds);

	// 대시보드: 전체 API 호출 총계
	long count();
}
