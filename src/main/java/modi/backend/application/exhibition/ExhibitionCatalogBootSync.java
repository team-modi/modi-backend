package modi.backend.application.exhibition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 동기화 직후 부팅 적재분(미분류 행)의 장르 분류를 <b>별도 데몬 스레드</b>로 1회 트리거한다 —
 * AI 배치 호출이 ApplicationRunner를 붙잡아 기동(readiness/헬스체크)을 지연시키지 않게 하기 위함.
 * 미분류가 없으면(이미 전량 분류) 빈 조회 no-op이라 저렴하다. 이후 신규분은 매일 자정
 * {@link ExhibitionSyncScheduler}가 동기화 직후 같은 방식으로 분류한다(장르 생성은 동기화 시점에만).
 * 인증키 미설정이면 {@link ExhibitionFacade#syncCatalog()} 내부에서 스킵되어 외부 호출 없이 0건으로 끝난다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ExhibitionCatalogBootSync implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ExhibitionCatalogBootSync.class);

	private final ExhibitionFacade exhibitionFacade;
	private final CatalogEnricher catalogEnricher;

	@Override
	public void run(ApplicationArguments args) {
		try {
			int synced = exhibitionFacade.syncCatalog();
			log.info("부팅 시 전시 카탈로그 동기화 신규 {}건", synced);
		} catch (RuntimeException e) {
			log.warn("부팅 시 전시 카탈로그 동기화 스킵(외부 불가) — {}", e.getMessage());
		}
		// 부팅 적재분 장르 분류 — readiness를 막지 않도록 데몬 스레드에서 1회 수행(실패해도 자정 동기화가 재시도).
		Thread genreBootEnrich = new Thread(() -> {
			try {
				catalogEnricher.enrichGenres();
			} catch (RuntimeException e) {
				log.warn("부팅 시 장르 분류 실패(자정 동기화에서 재시도): {}", e.getMessage());
			}
		}, "genre-boot-enrich");
		genreBootEnrich.setDaemon(true);
		genreBootEnrich.start();
	}
}
