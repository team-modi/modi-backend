-- 전시 수집 파이프라인 최적화 ERD ②(수집 파이프라인) — 이행 P-0/P-5, 1단계: 구조 신설(create).
--
-- 근거: 설계 §2(enrichment_job = 이 설계의 심장) · ADR-01(벤더 원본 보존, 재시도 파이프라인이 진짜 미완성) ·
--       설계 §4-1(이벤트 구동 영업시간 재검증). 요구: at-least-once(재시작 생존)는 코드가 아니라 "DB에 남는 작업 상태"로만 보장된다.
--
-- revertible 단계(생성 → 백필 V28 → 읽기 전환[코드] → drop V29)의 첫 단계다. 이 마이그레이션은 새 구조만 추가하고
-- 기존 동작을 바꾸지 않는다(어떤 컬럼도 지우지 않는다) — 되돌릴 수 있는 지점.


-- ── 통합 보강 작업큐(인박스 패턴) — 흩어진 상태머신을 하나로 ──────────────────────────
-- 현행은 진행 상태가 축마다 흩어져 있다: 상세=culture_detail_response의 status/attempt/next_attempt(그런데 쓰기만 되고
--   아무도 안 읽음 = 현행 최대 갭), 영업시간=place_hours의 반쪽, AI 장르=아예 없음. 요구사항(at-least-once·수동 재시도·
--   비용상한·재검증)을 축마다 3벌 구현하는 대신 여기 1벌로 모은다. 벤더 테이블은 원본만, 정준 테이블은 결과만,
--   "진행 상태는 이 테이블만 안다".
--   job_type: DETAIL_SYNC | GENRE_CLASSIFY | PLACE_HOURS_FETCH | PLACE_HOURS_REFRESH
--   target_key: external_id(상세·장르) 또는 place_key(영업시간) — 두 키 공간이 UK(job_type, target_key)로 분리된다.
--   status: PENDING → SUCCEEDED / FAILED_RETRYABLE(백오프 후 재선별) / FAILED_PERMANENT(4xx·파싱실패·시도초과, 사람이 봄)
--   version: 낙관락 — 스케줄러·수동트리거 동시 클레임 시 한쪽만 이기고 다른 쪽은 skip(다른 워커 선점).
create table enrichment_job (
    id bigint not null auto_increment,
    job_type varchar(30) not null,
    target_key varchar(500) not null,
    status varchar(20) not null,
    attempt_count int not null default 0,
    next_attempt_at datetime(6) null,
    last_error text null,
    version bigint not null default 0,
    completed_at datetime(6) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

-- enqueue 멱등 — 같은 (종류, 대상)에 중복 작업이 생기지 않는다(카탈로그 sync 한 번에 같은 장소 HOURS_REFRESH가 여럿 들어와도 1건).
create unique index uk_enrichment_job_type_target on enrichment_job (job_type, target_key);
-- 큐 선별의 핵심 — 폴링 쿼리(status IN (PENDING, FAILED_RETRYABLE) AND next_attempt_at <= now)가 풀스캔하지 않도록.
create index idx_enrichment_job_status_next on enrichment_job (status, next_attempt_at);


-- ── sync_run.trigger_type — "왜 이 시각에 돌았나" ──────────────────────────────────
-- 같은 syncCatalog라도 무엇이 불렀나(부팅/정기/수동)가 운영 판독에 중요하다. 현행 sync_run은 계기를 안 남겨
--   BOOT 재시작이 몰리는지, 수동 트리거가 돌았는지 알 수 없다. BOOT | SCHEDULE | MANUAL.
-- 기존 행은 계기를 소급 판별할 수 없다 — 대부분 정기/부팅 런이므로 기본 'SCHEDULE'로 채운다(과거의 근사, 문서화된 부정확).
alter table sync_run
    add column trigger_type varchar(20) not null default 'SCHEDULE';


-- ── place_hours.synced_at — 이벤트 구동 재검증의 최소 간격 기준(설계 §1·§4-1) ─────────
-- 새 전시가 기존 장소에 들어올 때 HOURS_REFRESH를 거는데, 이 값이 최근이면(설정 간격 이내) enqueue를 건너뛴다
--   (카탈로그 sync 한 번에 유료 호출 burst 방지). 전송 실패는 갱신하지 않는다 — "성공적으로 확인한 시각"이어야
--   다음 주기 재시도가 유지되고 간격 판정도 정확하다.
alter table place_hours
    add column synced_at datetime(6) null;

-- 기존 행 백필(best-effort): 구글 원본을 받아둔 장소는 그 fetched_at을 마지막 조회 시각으로 본다.
--   mock으로만 채워진 행(구글 원본 없음)은 null로 남는다 = "성공 확인 시각 모름" → 재검증 가드가 막지 않는다(허용).
update place_hours ph
    join google_place_response g on g.place_key = ph.place_key
    set ph.synced_at = g.fetched_at
    where ph.status <> 'FAILED';
