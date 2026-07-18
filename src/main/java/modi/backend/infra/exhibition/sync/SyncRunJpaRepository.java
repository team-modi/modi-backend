package modi.backend.infra.exhibition.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.sync.entity.SyncRun;

public interface SyncRunJpaRepository extends JpaRepository<SyncRun, Long> {
}
