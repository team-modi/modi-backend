package modi.backend.infra.exhibition.sync;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.sync.entity.SyncRun;
import modi.backend.domain.exhibition.sync.port.SyncRunRepository;

@Repository
@RequiredArgsConstructor
public class SyncRunRepositoryImpl implements SyncRunRepository {

	private final SyncRunJpaRepository jpaRepository;

	@Override
	public SyncRun save(SyncRun syncRun) {
		return jpaRepository.save(syncRun);
	}
}
