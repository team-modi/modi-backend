package modi.backend.application.exhibition.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 부팅 시 1회 공공데이터 전시 카탈로그 동기화(cold start 방지). 데모 시드 플래그와 무관하게 항상 실행한다
 * ({@link ExhibitionDemoSeeder}의 표본 적재만 데모 플래그로 게이팅되고, 이 실제 동기화는 항상 시도된다).
 * <p>
 * 동기화(목록+상세 한 패스)와 장르 분류를 모두 <b>별도 데몬 스레드</b>에서 수행한다 —
 * syncCatalog가 신규·미완성 행마다 상세 API를 호출(초기 적재 시 수백 콜)하고 이어서 AI 배치 분류까지 도는데,
 * 이를 ApplicationRunner에 두면 기동(readiness/헬스체크)이 그만큼 지연되기 때문이다. 이미 전량 동기화·분류된
 * 뒤엔 상세/AI 호출 없이 빈 조회 no-op이라 저렴하다. 이후 신규분은 매일 자정 {@link ExhibitionSyncScheduler}가 같은 방식으로 처리한다.
 * 인증키 미설정이면 {@link ExhibitionFacade#syncCatalog()} 내부에서 스킵되어 외부 호출 없이 0건으로 끝난다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ExhibitionCatalogBootSync implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ExhibitionCatalogBootSync.class);

	private final ExhibitionIngestFacade exhibitionIngestFacade;
	private final CatalogEnricher catalogEnricher;

	/** 로컬 시드 모드면 외부 동기화를 건너뛴다(로컬 실 API 호출 0 — {@link LocalExhibitionSeeder}가 SQL로 초기화). */
	@Value("${app.local-seed.enabled:false}")
	private boolean localSeedEnabled;

	@Override
	public void run(ApplicationArguments args) {
		if (localSeedEnabled) {
			log.info("부팅 카탈로그 동기화 skip — app.local-seed.enabled=true (로컬 시드로 초기화, data.go.kr 호출 안 함)");
			return;
		}
		// 목록+상세 동기화 후 장르 분류 — readiness를 막지 않도록 데몬 스레드에서 1회 수행(실패해도 자정 동기화가 재시도).
		Thread bootSync = new Thread(() -> {
			try {
				int synced = exhibitionIngestFacade.syncCatalog(modi.backend.domain.exhibition.sync.SyncTrigger.BOOT);
				log.info("부팅 시 전시 카탈로그 동기화(상세 포함) 신규 {}건", synced);
			} catch (RuntimeException e) {
				log.warn("부팅 시 전시 카탈로그 동기화 스킵(외부 불가) — {}", e.getMessage());
			}
			try {
				catalogEnricher.enrichGenres();
			} catch (RuntimeException e) {
				log.warn("부팅 시 장르 분류 실패(자정 동기화에서 재시도): {}", e.getMessage());
			}
			// 영업시간 보강은 부팅에서 하지 않는다 — 매 재시작마다 (운영 google 시) 유료 호출을 재발하지 않도록
			// 매일 자정 스케줄러({@link ExhibitionSyncScheduler})에서만 수행한다(신규/만료 장소만).
		}, "catalog-boot-sync");
		bootSync.setDaemon(true);
		bootSync.start();
	}
}
