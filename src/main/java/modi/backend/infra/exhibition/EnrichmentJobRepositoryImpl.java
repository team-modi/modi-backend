package modi.backend.infra.exhibition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.EnrichmentJob;
import modi.backend.domain.exhibition.EnrichmentJobRepository;
import modi.backend.domain.exhibition.JobStatus;
import modi.backend.domain.exhibition.JobType;

@Repository
@RequiredArgsConstructor
public class EnrichmentJobRepositoryImpl implements EnrichmentJobRepository {

	/** 선별 대상 상태(미종료) — 이 필터는 구현 세부라 포트가 아닌 어댑터가 주입한다. */
	private static final List<JobStatus> PENDING_STATUSES = List.of(JobStatus.PENDING, JobStatus.FAILED_RETRYABLE);

	private final EnrichmentJobJpaRepository jpaRepository;

	@Override
	public EnrichmentJob save(EnrichmentJob job) {
		return jpaRepository.save(job);
	}

	@Override
	public Optional<EnrichmentJob> findByJobTypeAndTargetKey(JobType jobType, String targetKey) {
		return jpaRepository.findByJobTypeAndTargetKey(jobType, targetKey);
	}

	@Override
	public List<EnrichmentJob> findDue(JobType jobType, LocalDateTime now, int limit) {
		return jpaRepository.findDue(jobType, PENDING_STATUSES, now, PageRequest.of(0, Math.max(1, limit)));
	}

	@Override
	public long countByStatus(JobStatus status) {
		return jpaRepository.countByStatus(status);
	}
}
