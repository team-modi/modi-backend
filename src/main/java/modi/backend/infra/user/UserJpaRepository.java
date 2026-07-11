package modi.backend.infra.user;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import modi.backend.domain.user.User;

public interface UserJpaRepository extends JpaRepository<User, Long> {

	/** soft delete된 행은 제외하고 조회. */
	Optional<User> findByIdAndDeletedAtIsNull(Long id);

	// ── 관리자 콘솔(집계/목록) 전용 조회 ───────────────────────────────

	/** 전체 가입 수(살아있는). */
	long countByDeletedAtIsNull();

	/** 관리자 사용자 목록 — 닉네임/실명 부분검색, 최신 가입순. */
	@Query("""
			select u from User u
			where u.deletedAt is null
			  and (:q is null or u.nickname like concat('%', :q, '%') or u.name like concat('%', :q, '%'))
			order by u.createdAt desc
			""")
	Page<User> searchForAdmin(@Param("q") String q, Pageable pageable);

	/** 일자별 신규 가입 수(created_at 기준, UTC 일자 — 대시보드 추이용). Object[]{date, count}. */
	@Query("select function('date', u.createdAt), count(u) from User u "
			+ "where u.deletedAt is null and u.createdAt >= :from "
			+ "group by function('date', u.createdAt) order by function('date', u.createdAt)")
	List<Object[]> countByDaySince(@Param("from") ZonedDateTime from);
}
