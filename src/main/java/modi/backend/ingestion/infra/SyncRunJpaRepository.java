package modi.backend.ingestion.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.ingestion.domain.entity.SyncRun;

public interface SyncRunJpaRepository extends JpaRepository<SyncRun, Long> {
}
