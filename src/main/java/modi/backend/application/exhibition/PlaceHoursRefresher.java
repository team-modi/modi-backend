package modi.backend.application.exhibition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import modi.backend.config.EnrichmentProperties;
import modi.backend.domain.exhibition.EnrichmentJob;
import modi.backend.domain.exhibition.JobType;
import modi.backend.domain.exhibition.OpeningHoursFormatter;
import modi.backend.domain.exhibition.PlaceHoursData;
import modi.backend.domain.exhibition.PlaceHoursProvider;

/**
 * 이벤트 구동 영업시간 재검증 처리기(설계 §4-1) — 통합 작업큐의 영업시간 작업
 * ({@link JobType#PLACE_HOURS_REFRESH}·{@link JobType#PLACE_HOURS_FETCH})을 드레인한다.
 *
 * <p>재검증 enqueue는 카탈로그 sync가 "새 전시가 기존 장소에 유입"될 때 걸고(가드: 기존 장소만·최소 간격·UK 중복),
 * 여기서 도래한 작업을 집어 그 장소의 영업시간을 다시 조회한다 — 같은 큐·같은 at-least-once·같은 비용상한이다.
 * 외부 호출은 트랜잭션 밖, 반영·상태 전이만 트랜잭션({@link PlaceHoursEnricher}와 동형). mock provider가 기본이라
 * 로컬·CI·develop에선 유료 호출 없이 동일 경로가 돈다.
 */
@Component
@RequiredArgsConstructor
public class PlaceHoursRefresher {

	private static final Logger log = LoggerFactory.getLogger(PlaceHoursRefresher.class);

	/** 최초 조회(FETCH)는 현재 {@link PlaceHoursEnricher}가 직접 하지만, 큐로 들어온 FETCH도 같은 경로로 처리한다. */
	private static final List<JobType> HOURS_JOB_TYPES = List.of(
			JobType.PLACE_HOURS_REFRESH, JobType.PLACE_HOURS_FETCH);

	private final EnrichmentJobFacade enrichmentJobFacade;
	private final ExhibitionFacade exhibitionFacade;
	private final PlaceHoursProvider placeHoursProvider;
	private final OpeningHoursFormatter openingHoursFormatter;
	private final EnrichmentProperties properties;

	/**
	 * 도래한 영업시간 작업(재검증·최초조회)을 배치로 처리한다. 도래 작업이 없으면 외부 호출 없이 끝난다.
	 *
	 * @return 이번 실행에서 상태를 전이시킨 작업 수(낙관락 skip 제외)
	 */
	public int refreshDueHours() {
		LocalDateTime now = LocalDateTime.now();
		int processed = 0;
		for (JobType jobType : HOURS_JOB_TYPES) {
			List<EnrichmentJob> jobs = enrichmentJobFacade.findDue(jobType, properties.jobBatchSize(), now);
			for (EnrichmentJob job : jobs) {
				if (processOne(job, now)) {
					processed++;
				}
			}
		}
		if (processed > 0) {
			log.info("영업시간 재검증 처리 {}건", processed);
		}
		return processed;
	}

	/** @return true면 전이함, false면 낙관락 충돌로 skip(다른 워커가 처리). */
	private boolean processOne(EnrichmentJob job, LocalDateTime now) {
		String placeKey = job.getTargetKey();
		Optional<PlaceHoursTarget> resolved = exhibitionFacade.resolvePlaceHoursTarget(placeKey);
		if (resolved.isEmpty()) {
			// 그 장소를 쓰는 전시가 더는 없다 — 재검증할 대상이 없으니 성공으로 마감한다.
			return EnrichmentJobProcessing.succeed(enrichmentJobFacade, job, now);
		}
		PlaceHoursTarget target = resolved.get();
		try {
			Optional<PlaceHoursData> data = placeHoursProvider.fetch(target.placeName(), target.placeAddr());
			String formatted = data.map(d -> openingHoursFormatter.format(d.weeklyHours())).orElse(null);
			exhibitionFacade.applyVenueHours(target, data.orElse(null), formatted, placeHoursProvider.vendor(), now);
		} catch (org.springframework.dao.OptimisticLockingFailureException e) {
			return false; // 반영 중 충돌 — 다른 워커가 처리
		} catch (RuntimeException e) {
			exhibitionFacade.recordVenueHoursFailure(target, placeHoursProvider.vendor());
			return EnrichmentJobProcessing.fail(enrichmentJobFacade, job,
					JobFailures.classify(e), JobFailures.describe(e), now);
		}
		return EnrichmentJobProcessing.succeed(enrichmentJobFacade, job, now);
	}
}
