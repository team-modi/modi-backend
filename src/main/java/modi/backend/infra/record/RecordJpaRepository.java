package modi.backend.infra.record;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import modi.backend.domain.record.Record;
import modi.backend.domain.record.WriteMode;

public interface RecordJpaRepository extends JpaRepository<Record, Long> {

	Optional<Record> findByIdAndDeletedAtIsNull(Long id);

	/** 해당 사용자가 이 전시에 대한 (살아있는) 기록을 가지고 있는지 — 전시 상세의 recorded 필드용. */
	boolean existsByUserIdAndExhibitionIdAndDeletedAtIsNull(Long userId, Long exhibitionId);

	/** 사용자의 (살아있는) 기록 수 — 프로필 통계용. */
	long countByUserIdAndDeletedAtIsNull(Long userId);

	/** 사용자가 기록을 남긴 서로 다른 전시 수(다녀온 전시) — 프로필 통계용. */
	@Query("select count(distinct r.exhibitionId) from Record r where r.userId = :userId and r.deletedAt is null")
	long countDistinctExhibitionByUserId(@Param("userId") Long userId);

	/** 사용자의 기록에 쓰인 감정 키워드 — 빈도 내림차순, 중복 제거(프로필 '나의 감정 키워드'용). */
	@Query("""
			select e.emotionCode
			from Record r join r.emotions e
			where r.userId = :userId and r.deletedAt is null
			group by e.emotionCode
			order by count(e) desc, e.emotionCode asc
			""")
	List<String> findEmotionCodesByUserIdOrderByFrequency(@Param("userId") Long userId);

	/** 감정까지 즉시 로딩해 반환(리마인드에서 원본 감정을 세션 밖에서 안전히 읽기 위함). */
	@Query("select distinct r from Record r left join fetch r.emotions where r.id = :id and r.deletedAt is null")
	Optional<Record> findByIdWithEmotions(@Param("id") Long id);

	@Query("""
			select distinct r
			from Record r
			left join r.keywords k
			left join r.emotions e
			where r.userId = :userId
			  and r.deletedAt is null
			  and (:keyword is null or r.content like concat('%', :keyword, '%')
			       or r.aiSummary like concat('%', :keyword, '%')
			       or k.keyword like concat('%', :keyword, '%'))
			  and (:emotion is null or e.emotionCode = :emotion)
			  and (:exhibitionId is null or r.exhibitionId = :exhibitionId)
			  and (:bookmarked is null or r.bookmarked = :bookmarked)
			  and (:writeMode is null or r.writeMode = :writeMode)
			  and (:fromViewedAt is null or r.viewedAt >= :fromViewedAt)
			  and (:toViewedAt is null or r.viewedAt <= :toViewedAt)
			""")
	Page<Record> search(
			@Param("userId") Long userId,
			@Param("keyword") String keyword,
			@Param("emotion") String emotion,
			@Param("exhibitionId") Long exhibitionId,
			@Param("bookmarked") Boolean bookmarked,
			@Param("writeMode") WriteMode writeMode,
			@Param("fromViewedAt") LocalDate fromViewedAt,
			@Param("toViewedAt") LocalDate toViewedAt,
			Pageable pageable);
}
