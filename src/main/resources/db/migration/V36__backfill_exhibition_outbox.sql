-- 전시 초기화 트랜잭션 아웃박스 재편(ADR-10) — 2단계: 데이터 백필(create → [backfill] → 읽기전환 → drop).
--
-- enrichment_job의 전 행을 exhibition_outbox로 이관한다. 진행 중(PENDING/FAILED_RETRYABLE)만이 아니라 종료 행
-- (SUCCEEDED/FAILED_PERMANENT)도 옮긴다 — 종료 이력이 멱등 enqueue("일회성 메시지는 종료됐으면 되살리지 않는다")와
-- 운영 판독(FAILED_PERMANENT 감시)의 근거이기 때문이다. 이 단계 없이 V37에서 drop하면 진행 중이던 재시도가 증발한다.
--
-- 타입 재명명 매핑(명령형): DETAIL_SYNC→FETCH_DETAIL · GENRE_CLASSIFY→CLASSIFY_GENRE ·
--                        PLACE_HOURS_FETCH→FETCH_PLACE_HOURS · PLACE_HOURS_REFRESH→REFRESH_PLACE_HOURS.
-- 상태·시도 횟수·백오프 시각·실패 원인을 그대로 보존한다(백오프 진행이 리셋되지 않는다).

insert into exhibition_outbox
    (message_type, target_key, status, attempt_count, next_attempt_at, last_error, version, completed_at, created_at, updated_at)
select
    case j.job_type
        when 'DETAIL_SYNC' then 'FETCH_DETAIL'
        when 'GENRE_CLASSIFY' then 'CLASSIFY_GENRE'
        when 'PLACE_HOURS_FETCH' then 'FETCH_PLACE_HOURS'
        when 'PLACE_HOURS_REFRESH' then 'REFRESH_PLACE_HOURS'
        else j.job_type
    end,
    j.target_key, j.status, j.attempt_count, j.next_attempt_at, j.last_error, 0, j.completed_at, j.created_at, j.updated_at
from enrichment_job j
where not exists (
    select 1 from exhibition_outbox o
    where o.message_type = case j.job_type
            when 'DETAIL_SYNC' then 'FETCH_DETAIL'
            when 'GENRE_CLASSIFY' then 'CLASSIFY_GENRE'
            when 'PLACE_HOURS_FETCH' then 'FETCH_PLACE_HOURS'
            when 'PLACE_HOURS_REFRESH' then 'REFRESH_PLACE_HOURS'
            else j.job_type
        end
      and o.target_key = j.target_key
);
