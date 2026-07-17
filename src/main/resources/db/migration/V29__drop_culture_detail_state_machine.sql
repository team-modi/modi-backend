-- 전시 수집 파이프라인 최적화 ERD ② — 이행 P-0, 4단계: drop(create → backfill → [읽기전환 코드] → drop).
--
-- 근거: 설계 §2(벤더 테이블은 순수 원본 보관소로 회귀) · ADR-01(벤더 원본 보존). 상태머신은 V27/V28에서
--       enrichment_job(DETAIL_SYNC)으로 이관됐고, 코드의 선별·재시도가 job 읽기로 전환된 뒤(같은 커밋 세트) 이 컬럼들을 지운다.
--
-- ⚠️ 순서 안전장치: 이 drop은 반드시 V28 백필 이후에 온다 — 먼저 지우면 진행 중이던 재시도가 job으로 옮겨지지 못하고 증발한다.
--    코드가 이 컬럼들을 더는 읽지 않음을 전제한다(CultureDetailResponse는 external_id·payload만 남는 원본 보관소로 축소됨).

drop index idx_culture_detail_response_status_next on culture_detail_response;

alter table culture_detail_response
    drop column status,
    drop column attempt_count,
    drop column next_attempt_at;
