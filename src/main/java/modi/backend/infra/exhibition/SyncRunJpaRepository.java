package modi.backend.infra.exhibition;

import org.springframework.data.jpa.repository.JpaRepository;

import modi.backend.domain.exhibition.SyncRun;

public interface SyncRunJpaRepository extends JpaRepository<SyncRun, Long> {
}
