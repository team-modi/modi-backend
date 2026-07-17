package modi.backend.application.exhibition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.exhibition.EnrichmentJob;
import modi.backend.domain.exhibition.EnrichmentJobRepository;
import modi.backend.domain.exhibition.ExhibitionPlace;
import modi.backend.domain.exhibition.ExhibitionPlaceRepository;
import modi.backend.domain.exhibition.JobFailureType;
import modi.backend.domain.exhibition.JobStatus;
import modi.backend.domain.exhibition.JobType;
import modi.backend.domain.exhibition.PlaceHours;
import modi.backend.domain.exhibition.PlaceHoursRepository;
import modi.backend.domain.exhibition.PlaceHoursStatus;
import modi.backend.domain.exhibition.PlaceHoursVendor;

/**
 * 통합 작업큐 파사드·저장소 통합 검증(@SpringBootTest + Testcontainers-MySQL) — enqueue 멱등(UK), 선별 쿼리(findDue),
 * 낙관락 충돌 skip, 이벤트 구동 영업시간 재검증 가드(최소 간격·기존 장소만·재활성화). 이 경로들은 JPA @Version·UK·
 * 인덱스가 실제로 걸려야 검증되므로 순수 단위가 아닌 통합으로 확인한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.exhibition.enrich.scheduling-enabled=false")
class EnrichmentJobFacadeIntegrationTest {

	private static final AtomicInteger SEQ = new AtomicInteger(1);

	@Autowired
	EnrichmentJobFacade enrichmentJobFacade;

	@Autowired
	EnrichmentJobRepository enrichmentJobRepository;

	@Autowired
	PlaceHoursRepository placeHoursRepository;

	@Autowired
	ExhibitionPlaceRepository exhibitionPlaceRepository;

	private String nextKey(String prefix) {
		return prefix + "-" + SEQ.getAndIncrement();
	}

	/**
	 * placeKey(=정규화 이름) 전시장 + 그 장소의 정준 영업시간(syncedAt)을 만든다. HOURS_REFRESH target_key는
	 * exhibition_place.place_key라, enqueueHoursRefresh가 이 전시장을 해소해 정준행을 본다(가드 판정).
	 */
	private void seedPlaceWithHours(String placeKey, LocalDateTime syncedAt) {
		Long placeId = exhibitionPlaceRepository.save(
				ExhibitionPlace.createFromList(placeKey, null, null, null, null)).getId();
		placeHoursRepository.save(PlaceHours.first(placeId, "매일 10:00~18:00",
				PlaceHoursStatus.SUCCEEDED, PlaceHoursVendor.GOOGLE, syncedAt));
	}

	@Test
	@DisplayName("enqueue 멱등 — 같은 (종류, 대상)을 두 번 넣어도 UK로 1건만 남는다")
	void enqueue_멱등() {
		String target = nextKey("EXT");
		LocalDateTime now = LocalDateTime.now();

		enrichmentJobFacade.enqueue(JobType.DETAIL_SYNC, target, now);
		enrichmentJobFacade.enqueue(JobType.DETAIL_SYNC, target, now.plusMinutes(5));

		List<EnrichmentJob> due = enrichmentJobFacade.findDue(JobType.DETAIL_SYNC, 100, now.plusHours(1));
		assertThat(due.stream().filter(j -> j.getTargetKey().equals(target))).hasSize(1);
	}

	@Test
	@DisplayName("findDue 선별 — 미종료이고 도래한 작업만, 도래 순으로 나온다(종료·미도래는 제외)")
	void findDue_선별() {
		LocalDateTime now = LocalDateTime.now();
		String dueKey = nextKey("EXT");
		String futureKey = nextKey("EXT");
		String doneKey = nextKey("EXT");

		enrichmentJobFacade.enqueue(JobType.DETAIL_SYNC, dueKey, now); // PENDING·now → 도래
		// 미도래: 실패로 next_attempt_at을 미래로 민다.
		enrichmentJobFacade.enqueue(JobType.DETAIL_SYNC, futureKey, now);
		EnrichmentJob future = enrichmentJobRepository
				.findByJobTypeAndTargetKey(JobType.DETAIL_SYNC, futureKey).orElseThrow();
		enrichmentJobFacade.markFailed(future, JobFailureType.RETRYABLE, "e", now);
		// 종료: 성공 처리.
		enrichmentJobFacade.enqueue(JobType.DETAIL_SYNC, doneKey, now);
		EnrichmentJob done = enrichmentJobRepository
				.findByJobTypeAndTargetKey(JobType.DETAIL_SYNC, doneKey).orElseThrow();
		enrichmentJobFacade.markSucceeded(done, now);

		List<String> due = enrichmentJobFacade.findDue(JobType.DETAIL_SYNC, 100, now).stream()
				.map(EnrichmentJob::getTargetKey).toList();

		assertThat(due).contains(dueKey).doesNotContain(futureKey, doneKey);
	}

	@Test
	@DisplayName("낙관락 충돌 — 같은 작업을 둘이 집어 둘 다 성공 전이하면 뒤쪽은 예외로 밀린다(다른 워커 선점 skip)")
	void 낙관락_충돌_skip() {
		String target = nextKey("EXT");
		LocalDateTime now = LocalDateTime.now();
		enrichmentJobFacade.enqueue(JobType.DETAIL_SYNC, target, now);

		// 두 워커가 각자 집은 두 detached 사본(같은 version).
		EnrichmentJob worker1 = enrichmentJobRepository
				.findByJobTypeAndTargetKey(JobType.DETAIL_SYNC, target).orElseThrow();
		EnrichmentJob worker2 = enrichmentJobRepository
				.findByJobTypeAndTargetKey(JobType.DETAIL_SYNC, target).orElseThrow();

		boolean first = EnrichmentJobProcessing.succeed(enrichmentJobFacade, worker1, now);
		boolean second = EnrichmentJobProcessing.succeed(enrichmentJobFacade, worker2, now);

		assertThat(first).isTrue(); // 이긴다
		assertThat(second).isFalse(); // 낙관락 충돌 → skip
		assertThat(enrichmentJobRepository.findByJobTypeAndTargetKey(JobType.DETAIL_SYNC, target)
				.orElseThrow().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
	}

	@Test
	@DisplayName("HOURS_REFRESH 가드 — place_hours가 없으면(기존 장소 아님) enqueue하지 않는다")
	void 재검증_기존장소만() {
		String placeKey = nextKey("PLACE");
		LocalDateTime now = LocalDateTime.now();

		enrichmentJobFacade.enqueueHoursRefresh(placeKey, now);

		assertThat(enrichmentJobRepository.findByJobTypeAndTargetKey(JobType.PLACE_HOURS_REFRESH, placeKey))
				.isEmpty();
	}

	@Test
	@DisplayName("HOURS_REFRESH 가드 — synced_at이 최소 간격 이내면 skip, 오래됐으면 enqueue한다")
	void 재검증_최소간격() {
		LocalDateTime now = LocalDateTime.now();
		String recentPlace = nextKey("PLACE");
		String stalePlace = nextKey("PLACE");
		// 최근 확인(1일 전) — 기본 최소 간격 30일 이내라 skip 대상.
		seedPlaceWithHours(recentPlace, now.minusDays(1));
		// 오래됨(60일 전) — 재검증 대상.
		seedPlaceWithHours(stalePlace, now.minusDays(60));

		enrichmentJobFacade.enqueueHoursRefresh(recentPlace, now);
		enrichmentJobFacade.enqueueHoursRefresh(stalePlace, now);

		assertThat(enrichmentJobRepository.findByJobTypeAndTargetKey(JobType.PLACE_HOURS_REFRESH, recentPlace))
				.isEmpty(); // 최소 간격 이내 → skip
		assertThat(enrichmentJobRepository.findByJobTypeAndTargetKey(JobType.PLACE_HOURS_REFRESH, stalePlace))
				.isPresent(); // 오래됨 → enqueue
	}

	@Test
	@DisplayName("HOURS_REFRESH 가드 — 종료된 이전 재검증 작업은 되살린다(재검증은 반복 이벤트)")
	void 재검증_종료작업_재활성화() {
		LocalDateTime now = LocalDateTime.now();
		String placeKey = nextKey("PLACE");
		seedPlaceWithHours(placeKey, now.minusDays(60));
		enrichmentJobFacade.enqueueHoursRefresh(placeKey, now);
		EnrichmentJob job = enrichmentJobRepository
				.findByJobTypeAndTargetKey(JobType.PLACE_HOURS_REFRESH, placeKey).orElseThrow();
		enrichmentJobFacade.markSucceeded(job, now); // 종료

		enrichmentJobFacade.enqueueHoursRefresh(placeKey, now.plusDays(40)); // 다시 오래됨 → 되살아나야 한다

		EnrichmentJob reactivated = enrichmentJobRepository
				.findByJobTypeAndTargetKey(JobType.PLACE_HOURS_REFRESH, placeKey).orElseThrow();
		assertThat(reactivated.getStatus()).isEqualTo(JobStatus.PENDING);
		assertThat(reactivated.getAttemptCount()).isZero();
	}
}
