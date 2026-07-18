package modi.backend.application.exhibition.sync.job;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import modi.backend.config.EnrichmentProperties;
import modi.backend.domain.exhibition.enrichment.EnrichmentJob;
import modi.backend.domain.exhibition.enrichment.EnrichmentJobRepository;
import modi.backend.domain.exhibition.catalog.ExhibitionPlace;
import modi.backend.domain.exhibition.catalog.ExhibitionPlaceRepository;
import modi.backend.domain.exhibition.enrichment.JobFailureType;
import modi.backend.domain.exhibition.enrichment.JobType;
import modi.backend.domain.exhibition.hours.PlaceHours;

/**
 * 통합 보강 작업큐 유스케이스 조율 — enqueue(멱등)·선별·상태 전이만 맡는다. 실제 외부 작업(상세 조회·AI 분류·
 * 영업시간 호출)은 각 처리기(enricher)가 <b>트랜잭션 밖</b>에서 하고, 여기 상태 전이 메서드를 트랜잭션으로 호출한다.
 *
 * <p>상태 변경은 전부 {@link EnrichmentJob} 메서드 안에서만 일어난다(Facade는 load·조율·save). 낙관락 충돌
 * ({@code OptimisticLockException})은 호출부(처리기)가 "다른 워커 선점"으로 보고 skip한다 — 여기서 삼키지 않는다.
 */
@Service
@RequiredArgsConstructor
public class EnrichmentJobFacade {

	private final EnrichmentJobRepository enrichmentJobRepository;
	/** 이벤트 구동 영업시간 재검증 가드(설계 §4-1)를 위해 정준 영업시간 테이블을 읽는다. */
	/** target_key(=exhibition_place.place_key, 정규화 이름 — ADR-07)로 전시장을 해소해 정준 영업시간을 찾는다. */
	private final ExhibitionPlaceRepository exhibitionPlaceRepository;
	private final EnrichmentProperties properties;

	/**
	 * 작업을 큐에 넣는다(멱등 — 같은 (종류, 대상)의 행이 이미 있으면 no-op). 이미 종료된 일회성 작업은 되살리지 않는다
	 * (상세·장르·최초조회는 한 대상당 한 번이면 족하다 — 재검증이 필요한 건 {@link #enqueueHoursRefresh}뿐이다).
	 */
	@Transactional
	public void enqueue(JobType jobType, String targetKey, LocalDateTime now) {
		if (targetKey == null || targetKey.isBlank()) {
			return;
		}
		if (enrichmentJobRepository.findByJobTypeAndTargetKey(jobType, targetKey).isPresent()) {
			return;
		}
		enrichmentJobRepository.save(EnrichmentJob.enqueue(jobType, targetKey, now));
	}

	/** 여러 대상을 한꺼번에 멱등 enqueue한다(장르 스윕처럼 다건 등록에 쓴다). */
	@Transactional
	public void enqueueAll(JobType jobType, Collection<String> targetKeys, LocalDateTime now) {
		for (String targetKey : targetKeys) {
			enqueue(jobType, targetKey, now);
		}
	}

	/**
	 * 이벤트 구동 영업시간 재검증 enqueue(설계 §4-1) — 새 전시가 <b>기존 장소</b>에 들어올 때 호출된다. 가드 2개:
	 * <ol>
	 *   <li><b>기존 장소만</b>: {@code place_hours} 행이 없으면(=최초 조회 전) 재검증 대상이 아니다(최초 FETCH는 별도 경로).</li>
	 *   <li><b>최소 간격</b>: {@code synced_at}이 설정 간격 이내면 skip — 한 번의 카탈로그 sync에 같은 장소로 전시가
	 *       쏟아져도 유료 호출이 burst하지 않게 한다.</li>
	 * </ol>
	 * UK 중복 방지는 {@link #enqueue}와 동일하나, 재검증은 <b>종료된 이전 작업을 되살린다</b>(reactivate) — 재검증은 반복 이벤트다.
	 */
	@Transactional
	public void enqueueHoursRefresh(String placeKey, LocalDateTime now) {
		if (placeKey == null || placeKey.isBlank()) {
			return;
		}
		// target_key는 exhibition_place.place_key(정규화 이름)다 — 전시장을 해소해 그 장소의 정준 영업시간을 본다.
		ExhibitionPlace place = exhibitionPlaceRepository.findByPlaceKey(placeKey).orElse(null);
		if (place == null) {
			return; // 아직 그 이름의 전시장이 없다(비정상 유입) — 재검증 대상 아님.
		}
		PlaceHours placeHours = exhibitionPlaceRepository.findHours(place.getId()).orElse(null);
		if (placeHours == null) {
			return; // 가드 1: 최초 조회 전 장소는 재검증 이벤트 대상이 아니다.
		}
		if (isRecentlySynced(placeHours, now)) {
			return; // 가드 2: 최근에 확인한 장소는 최소 간격 안이라 건너뛴다.
		}
		enrichmentJobRepository.findByJobTypeAndTargetKey(JobType.PLACE_HOURS_REFRESH, placeKey)
				.ifPresentOrElse(existing -> {
					existing.reactivate(now); // 종료됐던 작업이면 되살리고, 미종료면 no-op(이미 선별 대상).
					enrichmentJobRepository.save(existing);
				}, () -> enrichmentJobRepository.save(
						EnrichmentJob.enqueue(JobType.PLACE_HOURS_REFRESH, placeKey, now)));
	}

	private boolean isRecentlySynced(PlaceHours placeHours, LocalDateTime now) {
		LocalDateTime syncedAt = placeHours.getSyncedAt();
		if (syncedAt == null) {
			return false; // 성공 확인 시각을 모르면 막지 않는다(재검증 허용).
		}
		LocalDateTime threshold = now.minusDays(properties.hoursRefreshMinIntervalDays());
		return syncedAt.isAfter(threshold);
	}

	/** 선별 — 도래한 작업을 도래 순으로 최대 {@code limit}건 읽는다(외부 작업은 호출부가 트랜잭션 밖에서 수행). */
	@Transactional(readOnly = true)
	public List<EnrichmentJob> findDue(JobType jobType, int limit, LocalDateTime now) {
		return enrichmentJobRepository.findDue(jobType, now, limit);
	}

	/** 성공 전이 — 낙관락 충돌 시 예외를 전파한다(호출부가 skip). */
	@Transactional
	public void markSucceeded(EnrichmentJob job, LocalDateTime now) {
		job.succeed(now);
		enrichmentJobRepository.save(job);
	}

	/** 실패 전이(백오프·최대 초과 승격은 Entity가 정책으로 판단). 낙관락 충돌 시 예외 전파. */
	@Transactional
	public void markFailed(EnrichmentJob job, JobFailureType failureType, String error, LocalDateTime now) {
		job.recordFailure(failureType, error, properties.retryPolicy(), now);
		enrichmentJobRepository.save(job);
	}
}
