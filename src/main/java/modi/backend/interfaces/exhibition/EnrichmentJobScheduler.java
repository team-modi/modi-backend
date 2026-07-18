package modi.backend.interfaces.exhibition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import modi.backend.application.exhibition.sync.DetailEnricher;
import modi.backend.application.exhibition.sync.PlaceHoursRefresher;

/**
 * 통합 보강 작업큐의 <b>드레인 스케줄러</b> — 도래한(status·next_attempt_at ≤ now) 작업을 주기적으로 처리한다.
 *
 * <p>상세 재시도(DETAIL_SYNC)와 이벤트 구동 영업시간 재검증(PLACE_HOURS_REFRESH)은 정기 카탈로그 동기화와
 * 무관한 시점에 쌓이는 백로그라(상세 실패는 sync 중 언제든, 재검증은 새 전시 유입 이벤트로), 이 폴러가 주기적으로
 * 비운다. 장르(GENRE_CLASSIFY) 드레인은 동기화 직후 {@link CatalogEnricher}가 맡으므로 여기서 중복 처리하지 않는다.
 *
 * <p>각 처리기 실패는 서로를 막지 않도록 격리한다(하나가 던져도 다음 주기·다른 처리기는 계속). 스케줄 자체는
 * 기존 보강 스케줄과 같은 플래그({@code app.exhibition.enrich.scheduling-enabled})로 켜고 끈다(테스트에서 off).
 */
@Component
@ConditionalOnProperty(name = "app.exhibition.enrich.scheduling-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class EnrichmentJobScheduler {

	private static final Logger log = LoggerFactory.getLogger(EnrichmentJobScheduler.class);

	private final DetailEnricher detailEnricher;
	private final PlaceHoursRefresher placeHoursRefresher;

	/**
	 * 로컬 시드 모드면 드레인도 건너뛴다. 시드는 job을 만들지 않아 빈 큐 폴링만 돌아 무해하지만, "로컬은 외부 보강을 하지
	 * 않는다"는 의도를 명확히 하려고 명시적으로 skip한다(다른 스케줄러·BootSync와 동일 게이트).
	 */
	@Value("${app.local-seed.enabled:false}")
	private boolean localSeedEnabled;

	/** 주기적으로 상세 재시도·영업시간 재검증 작업을 드레인한다(at-least-once — 재시작 후에도 남은 작업이 이어서 처리됨). */
	@Scheduled(fixedDelayString = "${app.exhibition.enrichment.job-poll-interval-ms:60000}",
			initialDelayString = "${app.exhibition.enrichment.job-poll-interval-ms:60000}")
	public void drainJobs() {
		if (localSeedEnabled) {
			return; // 로컬 시드 모드 — 외부 보강 큐 드레인 안 함(로그 폭주 방지: 60초 폴링이라 침묵).
		}
		try {
			detailEnricher.enrichDetails();
		} catch (RuntimeException e) {
			log.warn("상세 재시도 드레인 실패(다음 주기 재시도): {}", e.getMessage());
		}
		try {
			placeHoursRefresher.refreshDueHours();
		} catch (RuntimeException e) {
			log.warn("영업시간 재검증 드레인 실패(다음 주기 재시도): {}", e.getMessage());
		}
	}
}
