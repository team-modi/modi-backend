-- 전시 수집 파이프라인 최적화 ERD ② — 이행 P-0, 2단계: 데이터 백필(create → [backfill] → 읽기전환 → drop).
--
-- 근거: ADR-01(재시도 파이프라인이 진짜 미완성) · at-least-once 요구(재시작 생존). 흩어져 있던 "미완료 진행 상태"를
--       통합 작업큐(enrichment_job)로 옮긴다. 이 단계 없이 V29에서 상태머신 컬럼을 drop하면 진행 중이던 재시도가 증발한다.
-- 선례: 데이터 백필은 마이그레이션 안에서(V21 exhibition_genre · V23 place_hours).


-- ── DETAIL_SYNC 백필 — 현행 최대 갭의 이관 ─────────────────────────────────────────
-- culture_detail_response의 status/attempt_count/next_attempt_at는 쓰기만 되고 아무도 안 읽었다. 그중 "아직 성공 못 한"
--   행(PENDING=미시도, FAILED=일시실패)만 재시도 대상이다. NO_DATA(원천에 상세 없음)·SUCCEEDED는 더 할 일이 없어 제외한다.
--   target_key = external_id. 기존 시도 횟수를 보존하고 즉시 선별되도록 status=PENDING·next_attempt_at=now로 넣는다.
insert into enrichment_job
    (job_type, target_key, status, attempt_count, next_attempt_at, version, created_at, updated_at)
select 'DETAIL_SYNC', d.external_id, 'PENDING', d.attempt_count, now(6), 0, now(6), now(6)
from culture_detail_response d
where d.status in ('PENDING', 'FAILED')
  and not exists (
      select 1 from enrichment_job j
      where j.job_type = 'DETAIL_SYNC' and j.target_key = d.external_id
  );


-- ── GENRE_CLASSIFY 백필 — "AI 최소 1회 무조건"의 미분류 잔여 ────────────────────────
-- 미분류(exhibition_genre 행 없음) CATALOG 전시를 분류 대기 작업으로 넣는다. AI 장애로 못 채운 것들이 회복 후
--   자동 재분류되도록. target_key = external_id(CATALOG는 external_id NOT NULL). CUSTOM은 등록 시 즉시 분류되므로 제외.
insert into enrichment_job
    (job_type, target_key, status, attempt_count, next_attempt_at, version, created_at, updated_at)
select 'GENRE_CLASSIFY', e.external_id, 'PENDING', 0, now(6), 0, now(6), now(6)
from exhibitions e
where e.type = 'CATALOG'
  and e.deleted_at is null
  and e.external_id is not null
  and not exists (select 1 from exhibition_genre g where g.exhibition_id = e.id)
  and not exists (
      select 1 from enrichment_job j
      where j.job_type = 'GENRE_CLASSIFY' and j.target_key = e.external_id
  );
