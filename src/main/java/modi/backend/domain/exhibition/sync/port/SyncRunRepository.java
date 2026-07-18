package modi.backend.domain.exhibition.sync.port;

import modi.backend.domain.exhibition.sync.entity.SyncRun;

/** 동기화 실행 기록 저장 포트(Spring 무의존). append-only라 저장만 제공한다. */
public interface SyncRunRepository {

	SyncRun save(SyncRun syncRun);
}
