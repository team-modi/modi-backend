package modi.backend.infra.exhibition.enrichment;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import modi.backend.domain.exhibition.enrichment.EnrichmentJob;
import modi.backend.domain.exhibition.enrichment.JobStatus;
import modi.backend.domain.exhibition.enrichment.JobType;

public interface EnrichmentJobJpaRepository extends JpaRepository<EnrichmentJob, Long> {

	Optional<EnrichmentJob> findByJobTypeAndTargetKey(JobType jobType, String targetKey);

	/**
	 * 선별 쿼리 — 인덱스 {@code (status, next_attempt_at)}를 타 도래 순으로 집는다.
	 * limit은 {@link Pageable}로 준다(파생 top-N은 상수만 되므로 값 제어를 위해 @Query + Pageable).
	 */
	@Query("select j from EnrichmentJob j "
			+ "where j.jobType = :jobType and j.status in :statuses and j.nextAttemptAt <= :now "
			+ "order by j.nextAttemptAt asc")
	List<EnrichmentJob> findDue(@Param("jobType") JobType jobType,
			@Param("statuses") Collection<JobStatus> statuses,
			@Param("now") LocalDateTime now, Pageable pageable);

	long countByStatus(JobStatus status);
}
