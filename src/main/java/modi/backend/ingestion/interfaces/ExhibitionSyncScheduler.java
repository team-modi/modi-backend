package modi.backend.ingestion.interfaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import modi.backend.ingestion.application.CatalogSynchronizer;
import modi.backend.ingestion.application.enricher.CatalogEnricher;
import modi.backend.ingestion.application.enricher.PlaceHoursEnricher;

/**
 * 공공데이터 전시 카탈로그 정기 동기화 스케줄러.
 * <p>
 * 매일 자정: 목록+상세 동기화({@link ExhibitionFacade#syncCatalog()}, <b>목록과 상세를 한 패스로 채워 완전한 행으로 적재</b>) →
 * 장르 분류({@link CatalogEnricher#enrichGenres()}, 방금 추가된 미분류 행만).
 * <p>
 * 상세(가격 등)는 syncCatalog가 적재 시점에 함께 채우므로 별도 상세 백필 잡이 없다.
 * 장르 키워드는 <b>동기화 직후에만</b> 생성된다 — 별도 주기 백필 없음. 대상이 "미분류 행"이라 멱등이고,
 * 신규가 없으면 빈 조회로 끝나 AI 호출을 태우지 않는다. 부팅 시 1회 동기화·장르 분류는
 * {@link ExhibitionCatalogBootSync}가 담당한다. 인증키 미설정이면 syncCatalog 내부에서 스킵되어 외부 호출 없이 끝난다.
 */
@Component
@ConditionalOnProperty(name = "app.exhibition.enrich.scheduling-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ExhibitionSyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(ExhibitionSyncScheduler.class);

	private final CatalogSynchronizer catalogSynchronizer;
	private final CatalogEnricher catalogEnricher;
	private final PlaceHoursEnricher placeHoursEnricher;

	/** 로컬 시드 모드면 정기 동기화도 건너뛴다(로컬 실 API 호출 0 — 시드 데이터 유지). */
	@Value("${app.local-seed.enabled:false}")
	private boolean localSeedEnabled;

	/** 매일 자정: 목록+상세를 한 패스로 적재/완성 → 신규분 장르 분류 → 신규/만료 장소 영업시간 보강. 실패해도 다음 주기에 재시도. */
	@Scheduled(cron = "${app.exhibition.sync.cron:0 0 0 * * *}")
	public void syncDaily() {
		if (localSeedEnabled) {
			log.info("전시 정기 동기화 skip — app.local-seed.enabled=true (로컬 시드 데이터 유지, 외부 API 호출 안 함)");
			return;
		}
		try {
			log.info("전시 정기 동기화 신규 {}건", catalogSynchronizer.syncCatalog());
			catalogEnricher.enrichGenres();
		} catch (RuntimeException e) {
			log.warn("전시 정기 동기화/보강 실패(다음 주기 재시도): {}", e.getMessage());
		}
		try {
			placeHoursEnricher.enrichPlaceHours();
		} catch (RuntimeException e) {
			// 영업시간은 부가 기능 — 실패해도 동기화/장르 결과에 영향 없음(다음 주기 재시도).
			log.warn("전시 영업시간 보강 실패(다음 주기 재시도): {}", e.getMessage());
		}
	}
}
