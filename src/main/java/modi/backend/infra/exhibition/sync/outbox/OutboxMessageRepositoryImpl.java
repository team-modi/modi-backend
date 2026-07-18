package modi.backend.infra.exhibition.sync.outbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessage;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessageRepository;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessageStatus;
import modi.backend.domain.exhibition.sync.outbox.OutboxMessageType;

@Repository
@RequiredArgsConstructor
public class OutboxMessageRepositoryImpl implements OutboxMessageRepository {

	/** 선별 대상 상태(미종료) — 이 필터는 구현 세부라 포트가 아닌 어댑터가 주입한다. */
	private static final List<OutboxMessageStatus> PENDING_STATUSES = List.of(OutboxMessageStatus.PENDING, OutboxMessageStatus.FAILED_RETRYABLE);

	private final OutboxMessageJpaRepository jpaRepository;

	@Override
	public OutboxMessage save(OutboxMessage job) {
		return jpaRepository.save(job);
	}

	@Override
	public Optional<OutboxMessage> findByMessageTypeAndTargetKey(OutboxMessageType messageType, String targetKey) {
		return jpaRepository.findByMessageTypeAndTargetKey(messageType, targetKey);
	}

	@Override
	public List<OutboxMessage> findDue(OutboxMessageType messageType, LocalDateTime now, int limit) {
		return jpaRepository.findDue(messageType, PENDING_STATUSES, now, PageRequest.of(0, Math.max(1, limit)));
	}

	@Override
	public long countByStatus(OutboxMessageStatus status) {
		return jpaRepository.countByStatus(status);
	}
}
