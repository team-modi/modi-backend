package modi.backend.ingestion.infra;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.ingestion.domain.entity.SyncRun;
import modi.backend.ingestion.domain.port.SyncRunRepository;

@Repository
@RequiredArgsConstructor
public class SyncRunRepositoryImpl implements SyncRunRepository {

	private final SyncRunJpaRepository jpaRepository;

	@Override
	public SyncRun save(SyncRun syncRun) {
		return jpaRepository.save(syncRun);
	}
}
