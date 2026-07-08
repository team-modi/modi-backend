package modi.backend.application.exhibition;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import modi.backend.config.CatalogEnrichProperties;
import modi.backend.config.PublicDataProperties;

/**
 * 공공데이터(CATALOG) 보강 오케스트레이션.
 * <p>
 * 목록 동기화({@link ExhibitionFacade#syncCatalog()})는 목록 필드만 적재하므로, 장르(AI 분류)와 가격 등 상세2 필드는
 * 여기서 별도로 채운다:
 * <ul>
 *   <li><b>장르</b> — 미분류 CATALOG를 배치로(배치당 AI 1콜) 상한 내 반복 분류해 전량 백필. 273건도 배치 크기 40이면 몇 콜로 끝난다.</li>
 *   <li><b>상세</b> — 상세 미수집 CATALOG를 행 단위로 상세 API 수집해 price 등을 채운다(무료 섹션이 실제로 뜨게 하는 근본 값).</li>
 * </ul>
 * 루프는 트랜잭션 밖에서 돌고 배치/행 단위 처리만 {@link ExhibitionFacade}의 트랜잭션 메서드(프록시 경유)로 위임해,
 * 다건 외부 호출(AI·상세 API)을 한 트랜잭션에 오래 물지 않는다. 대상이 "미처리 행"이라 멱등 — 반복 실행돼도 신규 행만 처리한다.
 */
@Component
@RequiredArgsConstructor
public class CatalogEnricher {

	private static final Logger log = LoggerFactory.getLogger(CatalogEnricher.class);

	private final ExhibitionFacade exhibitionFacade;
	private final CatalogEnrichProperties properties;
	private final PublicDataProperties publicDataProperties;

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

	/**
	 * 상세 미수집 CATALOG의 상세2(price 등)를 백필. 행마다 짧은 트랜잭션 + 상세 API 1회. 한 행이 실패해도 나머지는 계속한다.
	 *
	 * @return 이번 실행으로 상세를 채운 전시 수
	 */
	public int enrichDetails() {
		// 원천 키 미설정이면 fetchDetail이 전부 empty로 와 상세 없음으로 오인·표기될 수 있어 아예 스킵한다(불필요한 외부 호출도 방지).
		if (!publicDataProperties.isConfigured()) {
			return 0;
		}
		List<Long> ids = exhibitionFacade.findCatalogIdsWithoutDetail(properties.detailMaxPerRun());
		int done = 0;
		for (Long id : ids) {
			try {
				if (exhibitionFacade.syncCatalogDetail(id)) {
					done++;
				}
			} catch (RuntimeException e) {
				log.warn("전시 상세 백필 실패(id={}, 다음 주기 재시도): {}", id, e.getMessage());
			}
		}
		if (done > 0) {
			log.info("전시 상세(가격 등) 백필 {}건", done);
		}
		return done;
	}
}
