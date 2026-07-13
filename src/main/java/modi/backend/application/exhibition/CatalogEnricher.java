package modi.backend.application.exhibition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import modi.backend.config.CatalogEnrichProperties;

/**
 * 공공데이터(CATALOG) 장르 보강 오케스트레이션.
 * <p>
 * 목록 동기화({@link ExhibitionFacade#syncCatalog()})가 목록+상세를 한 패스로 채우므로, 여기서는 상세 대신 <b>장르(AI 분류)만</b> 담당한다:
 * 미분류 CATALOG를 배치로(배치당 AI 1콜) 상한 내 반복 분류해 전량 백필한다(273건도 배치 크기 40이면 몇 콜로 끝난다).
 * 루프는 트랜잭션 밖에서 돌고 배치 단위 처리만 {@link ExhibitionFacade}의 트랜잭션 메서드(프록시 경유)로 위임해,
 * 다건 AI 호출을 한 트랜잭션에 오래 물지 않는다. 대상이 "미분류 행"이라 멱등 — 반복 실행돼도 신규 행만 처리한다.
 */
@Component
@RequiredArgsConstructor
public class CatalogEnricher {

	private static final Logger log = LoggerFactory.getLogger(CatalogEnricher.class);

	private final ExhibitionFacade exhibitionFacade;
	private final CatalogEnrichProperties properties;

	/**
	 * 미분류 CATALOG 장르를 배치로 상한 내 전량 백필. 각 배치는 AI 1회 호출(배치가 batchSize 미만이면 미분류 소진 → 종료).
	 *
	 * @return 이번 실행으로 장르를 부여한 전시 수
	 */
	public int enrichGenres() {
		int batchSize = properties.genreBatchSize();
		int total = 0;
		for (int i = 0; i < properties.genreMaxBatchesPerRun(); i++) {
			int classified = exhibitionFacade.initGenres(batchSize);
			total += classified;
			if (classified < batchSize) {
				break; // 남은 미분류 없음 → 조기 종료(빈 배치로 AI를 태우지 않음)
			}
		}
		if (total > 0) {
			log.info("전시 장르 백필 {}건", total);
		}
		return total;
	}
}
