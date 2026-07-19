package modi.backend.interfaces.exhibition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import modi.backend.TestcontainersConfiguration;
import modi.backend.application.exhibition.sync.outbox.ExhibitionOutboxFacade;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessageRepository;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessageStatus;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessageType;

/**
 * 릴레이 이벤트 글루 통합 검증 — enqueue 트랜잭션 커밋 직후({@code AFTER_COMMIT}) 릴레이가 비동기로 드레인해
 * 폴링 주기를 기다리지 않고 메시지가 처리되는지 확인한다(ADR-10 "이벤트=글루"). 폴링은 1시간으로 사실상 꺼두므로
 * 이 테스트에서 상태 전이가 일어났다면 그 경로는 이벤트 드레인뿐이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"app.exhibition.enrich.scheduling-enabled=true",
		"app.exhibition.outbox.poll-interval-ms=3600000" // durable 엔진(폴링)은 꺼두고 글루(이벤트)만 남긴다
})
class ExhibitionOutboxRelayIntegrationTest {

	@Autowired
	ExhibitionOutboxFacade exhibitionOutboxFacade;

	@Autowired
	OutboxMessageRepository outboxMessageRepository;

	@Test
	@DisplayName("AFTER_COMMIT 드레인 — enqueue 커밋 직후 릴레이가 도래 메시지를 폴링 주기 없이 처리한다")
	void 커밋직후_이벤트드레인() throws InterruptedException {
		String target = "RELAY-" + System.nanoTime();

		// 대상 전시가 없는 FETCH_DETAIL — 드레인되면 "전시 미적재(다음 sync가 적재 예정)"로 RETRYABLE 전이된다.
		// 이 전이가 곧 "이벤트 드레인이 돌았다"의 관측 가능한 증거다(외부 호출 없음).
		exhibitionOutboxFacade.enqueue(OutboxMessageType.FETCH_DETAIL, target, LocalDateTime.now());

		OutboxMessageStatus observed = null;
		long deadline = System.currentTimeMillis() + 15_000;
		while (System.currentTimeMillis() < deadline) {
			observed = outboxMessageRepository
					.findByMessageTypeAndTargetKey(OutboxMessageType.FETCH_DETAIL, target)
					.orElseThrow().getStatus();
			if (observed == OutboxMessageStatus.FAILED_RETRYABLE) {
				break;
			}
			Thread.sleep(200);
		}

		assertThat(observed).isEqualTo(OutboxMessageStatus.FAILED_RETRYABLE);
	}
}
