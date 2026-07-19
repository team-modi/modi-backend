package modi.backend.ingestion.infra;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import modi.backend.ingestion.domain.entity.IngestionRun;
import modi.backend.ingestion.domain.port.IngestionRunRepository;

@Repository
@RequiredArgsConstructor
public class IngestionRunRepositoryImpl implements IngestionRunRepository {

	private final IngestionRunJpaRepository jpaRepository;

	@Override
	public IngestionRun save(IngestionRun syncRun) {
		return jpaRepository.save(syncRun);
	}
}
