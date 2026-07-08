package modi.backend.application.exhibition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 공공데이터 전시 카탈로그 정기 동기화 + 보강 스케줄러.
 * <ul>
 *   <li>매시 정각: 목록 동기화({@link ExhibitionFacade#syncCatalog()}) 후 장르·상세 보강({@link CatalogEnricher}).</li>
 *   <li>부팅 직후: 부팅 동기화({@link ExhibitionCatalogBootSync})로 적재된 백로그(장르 미분류·상세 미수집)를
 *       정각을 기다리지 않고 채운다. ApplicationRunner가 아니라 스케줄러라 readiness/헬스체크를 막지 않아,
 *       상세 백필의 다건 외부 호출로 배포 부팅이 지연·타임아웃되지 않는다.</li>
 * </ul>
 * 보강 대상은 "미처리 행"이라 다 채워지면 이후 실행은 사실상 no-op(빈 조회)으로 저렴하다.
 * 인증키 미설정이면 {@link ExhibitionFacade#syncCatalog()} 내부에서 스킵되어 외부 호출 없이 끝난다.
 */
@Component
@ConditionalOnProperty(name = "app.exhibition.enrich.scheduling-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ExhibitionSyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(ExhibitionSyncScheduler.class);

	private final ExhibitionFacade exhibitionFacade;
	private final CatalogEnricher catalogEnricher;

	/** 매시 정각 공공데이터 전시 동기화 + 장르·상세 보강. 실패해도 다음 주기에 재시도. */
	@Scheduled(cron = "0 0 * * * *")
	public void syncHourly() {
		try {
			log.info("전시 정기 동기화 {}건", exhibitionFacade.syncCatalog());
			catalogEnricher.enrichGenres();
			catalogEnricher.enrichDetails();
		} catch (RuntimeException e) {
			log.warn("전시 정기 동기화/보강 실패(다음 주기 재시도): {}", e.getMessage());
		}
	}

	/**
	 * 부팅 직후 1차 보강 + 이후 주기 보강. 정각 동기화가 미룬 백로그를 빠르게 채우고, 새로 적재된 행도 놓치지 않는다.
	 * (대상이 소진되면 빈 조회라 저렴 — AI/외부 호출을 태우지 않는다.)
	 */
	@Scheduled(initialDelayString = "${app.exhibition.enrich.startup-delay-ms:15000}",
			fixedDelayString = "${app.exhibition.enrich.interval-ms:600000}")
	public void enrichPeriodically() {
		try {
			catalogEnricher.enrichGenres();
			catalogEnricher.enrichDetails();
		} catch (RuntimeException e) {
			log.warn("전시 보강 실패(다음 주기 재시도): {}", e.getMessage());
		}
	}
}
