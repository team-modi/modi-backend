-- 고아 개인(CUSTOM) 전시 정리 — 기록 삭제 시 개인 전시가 함께 삭제되지 않던 버그로 남은 데이터를 청소한다.
-- 대상: 살아있는(soft-delete 안 된) CUSTOM 전시 중, 어떤 살아있는 기록도 참조하지 않는 것(= 고아).
-- 기록이 아직 달려 있는 개인 전시는 건드리지 않는다(상세 접근 유지). soft-delete(deleted_at)만 하며 물리 삭제는 하지 않는다.
UPDATE exhibitions e
SET e.deleted_at = NOW()
WHERE e.type = 'CUSTOM'
  AND e.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM records r
    WHERE r.exhibition_id = e.id
      AND r.deleted_at IS NULL
  );
