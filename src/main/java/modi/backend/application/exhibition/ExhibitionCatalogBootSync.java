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
 * 인증키 미설정이면 {@link ExhibitionFacade#syncCatalog()} 내부에서 스킵되어 외부 호출 없이 0건으로 끝난다
 * (테스트 환경 기본값 — CULTURE_API_KEY 미설정 → 컨텍스트 기동마다 안전하게 no-op).
 * 매시 정각 재동기화는 {@link ExhibitionSyncScheduler}가 담당한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ExhibitionCatalogBootSync implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ExhibitionCatalogBootSync.class);

	private final ExhibitionFacade exhibitionFacade;

	@Override
	public void run(ApplicationArguments args) {
		try {
			int synced = exhibitionFacade.syncCatalog();
			log.info("부팅 시 전시 카탈로그 동기화 {}건", synced);
		} catch (RuntimeException e) {
			log.warn("부팅 시 전시 카탈로그 동기화 스킵(외부 불가) — {}", e.getMessage());
		}
	}
}
