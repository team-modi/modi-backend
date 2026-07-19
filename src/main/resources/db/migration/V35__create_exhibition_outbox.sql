-- 전시 초기화 트랜잭션 아웃박스 재편(ADR-10) — 1단계: 구조 신설(create).
--
-- 근거: enrichment_job은 인박스(작업큐)로서 at-least-once를 이미 보장했지만, enqueue가 상태 변경 트랜잭션 밖
--       best-effort라 "전시는 저장됐는데 후속 작업 기록이 유실되는" 창이 있었다. 아웃박스의 본질은 방향이 아니라
--       원자성이다 — 메시지는 그것을 필요하게 만든 상태 변경과 같은 트랜잭션에서 기록된다(코드 전환과 함께).
--       용어도 아웃박스 어휘로 재편한다: job → message, 드레인 스케줄러 → 릴레이(relay).
--
-- revertible 단계(생성 → 백필 V36 → 읽기 전환[코드] → drop V37)의 첫 단계다 — V27~V29와 같은 규율.
-- 이 마이그레이션은 새 구조만 추가하고 기존 동작을 바꾸지 않는다(어떤 컬럼도 지우지 않는다) — 되돌릴 수 있는 지점.

-- ── exhibition_outbox — 스키마는 enrichment_job과 동형(검증된 상태머신을 유지), 어휘만 아웃박스로 ─────────
--   message_type: FETCH_DETAIL | CLASSIFY_GENRE | FETCH_PLACE_HOURS | REFRESH_PLACE_HOURS (명령형 재명명)
--   target_key: external_id(상세·장르) 또는 place_key(영업시간) — 두 키 공간이 UK(message_type, target_key)로 분리된다.
--   status: PENDING → SUCCEEDED / FAILED_RETRYABLE(백오프 후 재선별) / FAILED_PERMANENT(4xx·파싱실패·시도초과, 사람이 봄)
--   version: 낙관락 — 릴레이 폴링·이벤트 드레인 동시 클레임 시 한쪽만 이기고 다른 쪽은 skip(다른 워커 선점).
create table exhibition_outbox (
    id bigint not null auto_increment,
    message_type varchar(30) not null,
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

-- enqueue 멱등 — 같은 (종류, 대상)에 중복 메시지가 생기지 않는다(sync 한 번에 같은 장소 REFRESH가 여럿 들어와도 1건).
create unique index uk_exhibition_outbox_type_target on exhibition_outbox (message_type, target_key);
-- 선별의 핵심 — 릴레이 폴링 쿼리(status IN (PENDING, FAILED_RETRYABLE) AND next_attempt_at <= now)가 풀스캔하지 않도록.
create index idx_exhibition_outbox_status_next on exhibition_outbox (status, next_attempt_at);
