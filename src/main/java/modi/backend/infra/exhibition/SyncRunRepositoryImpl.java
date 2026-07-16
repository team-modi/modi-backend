package modi.backend.infra.exhibition;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.domain.exhibition.SyncRun;
import modi.backend.domain.exhibition.SyncRunRepository;

@Repository
@RequiredArgsConstructor
public class SyncRunRepositoryImpl implements SyncRunRepository {

	private final SyncRunJpaRepository jpaRepository;

	@Override
	public SyncRun save(SyncRun syncRun) {
		return jpaRepository.save(syncRun);
	}
}
