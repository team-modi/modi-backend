package modi.backend.application.exhibition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 매시 정각 공공데이터 전시 카탈로그 정기 동기화. 부팅 시 1회 동기화({@link ExhibitionCatalogBootSync})로
 * cold start를 메운 뒤, 이 스케줄러가 주기적으로 최신 상태를 유지한다.
 * 인증키 미설정이면 {@link ExhibitionFacade#syncCatalog()} 내부에서 스킵되어 외부 호출 없이 0건으로 끝난다.
 */
@Component
@RequiredArgsConstructor
public class ExhibitionSyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(ExhibitionSyncScheduler.class);

	private final ExhibitionFacade exhibitionFacade;

	/** 매시 정각 공공데이터 전시 동기화. 실패해도 다음 주기에 재시도. */
	@Scheduled(cron = "0 0 * * * *")
	public void syncHourly() {
		try {
			log.info("전시 정기 동기화 {}건", exhibitionFacade.syncCatalog());
		} catch (RuntimeException e) {
			log.warn("전시 정기 동기화 실패(다음 주기 재시도): {}", e.getMessage());
		}
	}
}
