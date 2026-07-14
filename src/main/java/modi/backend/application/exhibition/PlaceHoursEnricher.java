package modi.backend.application.exhibition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import modi.backend.config.PlaceHoursProperties;
import modi.backend.domain.exhibition.OpeningHoursFormatter;
import modi.backend.domain.exhibition.PlaceHoursData;
import modi.backend.domain.exhibition.PlaceHoursProvider;

/**
 * 전시 영업시간(운영시간) 보강 오케스트레이션 — 장르 보강({@link CatalogEnricher})과 동형.
 * <p>
 * 흐름: 스테이징 초기화 → 조회 대상(주소 있고 미조회·만료)을 장소 단위로 묶어 조회({@link PlaceHoursProvider}, 장소당 1콜) →
 * 원본 적재 + 우리 표시 규칙({@link OpeningHoursFormatter})으로 파생 저장. 루프는 트랜잭션 밖에서 돌고, 장소 단위 저장만
 * {@link ExhibitionFacade}의 트랜잭션 메서드(프록시 경유)로 위임해 외부 호출을 한 트랜잭션에 오래 물지 않는다(커넥션 장기 점유 방지).
 * <p>
 * 영업시간은 <b>부가 기능</b>이라 어떤 실패(조회 전송오류 등)도 동기화·등록 흐름을 깨지 않는다 — 해당 장소만 스킵하고 다음 주기에 재시도한다.
 * mock provider가 기본이라 로컬·CI·develop에서는 유료 호출 없이 동일 경로가 돈다.
 */
@Component
@RequiredArgsConstructor
public class PlaceHoursEnricher {

	private static final Logger log = LoggerFactory.getLogger(PlaceHoursEnricher.class);

	private final ExhibitionFacade exhibitionFacade;
	private final PlaceHoursProvider placeHoursProvider;
	private final OpeningHoursFormatter openingHoursFormatter;
	private final PlaceHoursProperties properties;

	/**
	 * 조회 대상 장소들의 영업시간을 채운다(장소당 1콜). 스테디 상태(전부 최신)에선 대상이 비어 외부 호출 없이 끝난다.
	 *
	 * @return 이번 실행으로 영업시간을 반영한(또는 조회 시도한) 전시 수
	 */
	public int enrichPlaceHours() {
		exhibitionFacade.resetPlaceHoursSnapshots();
		LocalDateTime staleBefore = LocalDateTime.now().minusDays(properties.refreshAfterDays());
		List<PlaceHoursTarget> targets = exhibitionFacade.findVenuesNeedingHours(staleBefore, properties.maxVenuesPerRun());
		if (targets.isEmpty()) {
			return 0;
		}
		int touched = 0;
		for (PlaceHoursTarget target : targets) {
			try {
				Optional<PlaceHoursData> data = placeHoursProvider.fetch(target.placeName(), target.placeAddr());
				String formatted = data.map(d -> openingHoursFormatter.format(d.weeklyHours())).orElse(null);
				exhibitionFacade.applyVenueHours(target, data.orElse(null), formatted, LocalDateTime.now());
				touched += target.exhibitionIds().size();
			} catch (RuntimeException e) {
				// 전송 실패 등 — 이 장소만 건너뛰고 synced_at을 남기지 않아 다음 주기에 재시도한다.
				log.warn("전시 영업시간 조회 실패(장소={}, 다음 주기 재시도): {}", target.placeAddr(), e.getMessage());
			}
		}
		if (touched > 0) {
			log.info("전시 영업시간 보강 {}건({} 장소)", touched, targets.size());
		}
		return touched;
	}
}
