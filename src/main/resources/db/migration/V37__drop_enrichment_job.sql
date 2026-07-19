-- 전시 초기화 트랜잭션 아웃박스 재편(ADR-10) — 4단계: 구질서 철거(create → backfill → 읽기전환[코드] → [drop]).
--
-- 읽기·쓰기가 전부 exhibition_outbox로 전환된 뒤(같은 릴리스의 코드), 인박스 시절의 enrichment_job을 걷어낸다.
-- 병존 금지(사용자 확정) — 두 테이블이 함께 남으면 "진행 상태의 진실"이 둘이 된다. V36이 전 행을 이관했으므로
-- 여기서 잃는 데이터는 없다.

drop table enrichment_job;
