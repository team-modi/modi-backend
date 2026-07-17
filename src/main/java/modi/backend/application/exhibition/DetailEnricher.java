package modi.backend.application.exhibition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import modi.backend.config.EnrichmentProperties;
import modi.backend.domain.exhibition.CatalogDetailData;
import modi.backend.domain.exhibition.EnrichmentJob;
import modi.backend.domain.exhibition.ExhibitionCatalogClient;
import modi.backend.domain.exhibition.JobFailureType;
import modi.backend.domain.exhibition.JobType;

/**
 * 상세(detail2) 재시도 처리기 — <b>현행 최대 갭의 해소</b>. 예전엔 상세 실패의 재시도 상태가
 * {@code culture_detail_response}에 쓰이기만 하고 아무도 읽지 않았다. 이제 선별을 통합 작업큐
 * ({@link JobType#DETAIL_SYNC})<b>읽기</b>로 배선한다: 도래한 작업만 집어 상세를 다시 조회한다.
 *
 * <p>외부 호출({@code fetchDetail})은 트랜잭션 밖에서 하고 반영·상태 전이만 트랜잭션에 위임한다({@link CatalogEnricher}·
 * {@link PlaceHoursEnricher}와 동형). timeout·5xx·429는 백오프 재시도(RETRYABLE), 4xx·파싱실패는 즉시 영구 실패,
 * 최대 시도 초과는 RETRYABLE도 PERMANENT로 승격한다({@link JobFailures}).
 */
@Component
@RequiredArgsConstructor
public class DetailEnricher {

	private static final Logger log = LoggerFactory.getLogger(DetailEnricher.class);

	private final EnrichmentJobFacade enrichmentJobFacade;
	private final ExhibitionFacade exhibitionFacade;
	private final ExhibitionCatalogClient catalogClient;
	private final EnrichmentProperties properties;

	/**
	 * 도래한 DETAIL_SYNC 작업을 배치로 처리한다. 스테디 상태(재시도 없음)에선 도래 작업이 없어 외부 호출 없이 끝난다.
	 *
	 * @return 이번 실행에서 상태를 전이시킨(성공·실패) 작업 수(낙관락 skip 제외)
	 */
	public int enrichDetails() {
		LocalDateTime now = LocalDateTime.now();
		List<EnrichmentJob> jobs = enrichmentJobFacade.findDue(JobType.DETAIL_SYNC, properties.jobBatchSize(), now);
		int processed = 0;
		for (EnrichmentJob job : jobs) {
			if (processOne(job, now)) {
				processed++;
			}
		}
		if (processed > 0) {
			log.info("상세 재시도 처리 {}건", processed);
		}
		return processed;
	}

	/** @return true면 전이함(성공/실패 기록), false면 낙관락 충돌로 skip(다른 워커가 처리). */
	private boolean processOne(EnrichmentJob job, LocalDateTime now) {
		String externalId = job.getTargetKey();
		DetailTargetState state = exhibitionFacade.findDetailTargetState(externalId);
		if (state == DetailTargetState.ALREADY_SYNCED) {
			// 다른 경로(정기 sync)가 이미 상세를 채웠다 — 할 일 없음.
			return EnrichmentJobProcessing.succeed(enrichmentJobFacade, job, now);
		}
		if (state == DetailTargetState.MISSING) {
			// 신규 전시가 상세 실패로 아직 적재 안 됨 — 다음 카탈로그 sync가 목록으로 적재한다. 그때까지 재시도 대상으로 둔다.
			return EnrichmentJobProcessing.fail(enrichmentJobFacade, job, JobFailureType.RETRYABLE,
					"전시 미적재 — 다음 카탈로그 동기화가 적재 예정", now);
		}
		Optional<CatalogDetailData> detail;
		try {
			detail = catalogClient.fetchDetail(externalId); // 트랜잭션 밖 외부 호출
		} catch (RuntimeException e) {
			return EnrichmentJobProcessing.fail(enrichmentJobFacade, job,
					JobFailures.classify(e), JobFailures.describe(e), now);
		}
		try {
			detail.ifPresentOrElse(d -> exhibitionFacade.applyDetailForJob(externalId, d),
					() -> exhibitionFacade.markDetailCheckedForJob(externalId));
		} catch (org.springframework.dao.OptimisticLockingFailureException e) {
			return false; // 반영 중 충돌 — 다른 워커가 처리
		} catch (RuntimeException e) {
			return EnrichmentJobProcessing.fail(enrichmentJobFacade, job,
					JobFailures.classify(e), JobFailures.describe(e), now);
		}
		return EnrichmentJobProcessing.succeed(enrichmentJobFacade, job, now);
	}
}
