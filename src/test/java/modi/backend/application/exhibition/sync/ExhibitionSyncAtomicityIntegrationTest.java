package modi.backend.application.exhibition.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import modi.backend.TestcontainersConfiguration;
import modi.backend.domain.exhibition.catalog.ExhibitionCategory;
import modi.backend.domain.exhibition.catalog.ExhibitionPlace;
import modi.backend.domain.exhibition.catalog.ExhibitionPlaceRepository;
import modi.backend.domain.exhibition.catalog.ExhibitionRegion;
import modi.backend.domain.exhibition.catalog.ExhibitionRepository;
import modi.backend.domain.exhibition.hours.PlaceHours;
import modi.backend.domain.exhibition.hours.PlaceHoursStatus;
import modi.backend.domain.exhibition.hours.PlaceHoursVendor;
import modi.backend.domain.exhibition.sync.data.CatalogExhibitionData;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessage;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessageRepository;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessageStatus;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessageType;
import modi.backend.infra.exhibition.hours.PlaceHoursJpaRepository;

/**
 * 아웃박스 원자성(ADR-10) 통합 검증 — 신규 목록 1건의 영속 단계({@code applyNewListing})에서
 * [전시장 resolve + 전시 저장 + 영업시간 재검증 enqueue]가 한 트랜잭션의 산물로 <b>함께</b> 남는지 확인한다.
 * 예전 구조(각 저장 자체 트랜잭션 + enqueue best-effort)에서는 전시만 남고 메시지가 유실되는 창이 있었다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.exhibition.enrich.scheduling-enabled=false")
class ExhibitionSyncAtomicityIntegrationTest {

	private static final AtomicInteger SEQ = new AtomicInteger(1);

	@Autowired
	ExhibitionSyncFacade exhibitionSyncFacade;

	@Autowired
	ExhibitionRepository exhibitionRepository;

	@Autowired
	ExhibitionPlaceRepository exhibitionPlaceRepository;

	@Autowired
	PlaceHoursJpaRepository placeHoursRepository;

	@Autowired
	OutboxMessageRepository outboxMessageRepository;

	@Test
	@DisplayName("applyNewListing — 전시 저장과 REFRESH_PLACE_HOURS enqueue가 한 트랜잭션의 산물로 함께 남는다")
	void applyNewListing_원자적_enqueue() {
		int seq = SEQ.getAndIncrement();
		String placeName = "원자성장소" + seq;
		String externalId = "ATOMIC-" + seq;
		LocalDateTime now = LocalDateTime.now();
		// 기존 장소 + 오래된(60일 전) 정준 영업시간 — 재검증 가드(기존 장소만·최소 간격)를 통과하는 조건.
		Long placeId = exhibitionPlaceRepository.save(
				ExhibitionPlace.createFromList(placeName, null, null, null, null)).getId();
		placeHoursRepository.save(PlaceHours.first(placeId, "매일 10:00~18:00",
				PlaceHoursStatus.SUCCEEDED, PlaceHoursVendor.GOOGLE, now.minusDays(60)));
		LocalDate today = LocalDate.now();
		CatalogExhibitionData data = new CatalogExhibitionData(externalId, "원자성 전시", placeName,
				today.minusDays(1), today.plusDays(10), ExhibitionRegion.SEOUL, ExhibitionCategory.PAINTING,
				null, null, "기관", null, null, null, "전시", "서울", null);

		exhibitionSyncFacade.applyNewListing(data, null); // 원천 상세 없음 — 확인 완료행만

		assertThat(exhibitionRepository.findByExternalId(externalId)).isPresent(); // 전시가 남았고
		String placeKey = exhibitionPlaceRepository.findById(placeId).orElseThrow().getPlaceKey();
		OutboxMessage message = outboxMessageRepository
				.findByMessageTypeAndTargetKey(OutboxMessageType.REFRESH_PLACE_HOURS, placeKey).orElseThrow();
		assertThat(message.getStatus()).isEqualTo(OutboxMessageStatus.PENDING); // 같은 트랜잭션의 메시지도 남았다
	}
}
