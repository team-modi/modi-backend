-- =====================================================================
-- 로컬 더미 보강 시드 (스냅샷 시드 직후 실행) — 전시 도메인 ERD(V34) 스키마 버전
-- 목적: 부팅 시 외부 API(구글 영업시간·AI 장르·상세) 호출 없이도
--       화면에 보강 데이터가 "다 채워진" 상태로 보이게 임의 값으로 초기화.
-- 실행: scripts/reset-local-exhibition-data.sh 가 스냅샷 시드 다음에 자동 실행.
-- 값 표식: 더미임을 알 수 있게 description에 [로컬 더미] 프리픽스, provider는 MOCK/UNKNOWN.
-- 멱등: 재실행해도 안전 (부재 행만 생성, NULL 값만 채움).
-- =====================================================================
-- 한글 값이 있으므로 클라이언트 캐릭터셋을 명시한다 (mysqldump 출력과 동일한 이유).
-- 없으면 docker exec 파이프 경로에서 latin1로 해석돼 더미 한글이 깨진다.
SET NAMES utf8mb4;

-- 1) 모든 전시장에 임의 영업시간 — 3패턴 로테이션, provider=MOCK (장소당 1행 = 유료 dedup 구조 그대로)
INSERT IGNORE INTO place_hours (exhibition_place_id, formatted, status, provider, attempt_count, next_attempt_at)
SELECT t.id,
       CASE MOD(t.rn, 3)
         WHEN 0 THEN '화~일 10:00~18:00 (월요일 휴관)'
         WHEN 1 THEN '매일 09:30~17:30'
         ELSE        '수~월 11:00~19:00 (화요일 휴관)'
       END,
       'SUCCEEDED', 'MOCK', 1, NULL
FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM exhibition_place) t;

-- 2) 장르 공백 보강 — provider=UNKNOWN(V21 백필 선례)
INSERT INTO exhibition_genre (exhibition_id, genre_keyword, provider, model, classified_at)
SELECT e.id,
       ELT(1 + MOD(e.id, 5), '회화', '미디어아트', '사진', '조각', '디자인'),
       'UNKNOWN', NULL, NOW(6)
FROM exhibitions e
WHERE NOT EXISTS (SELECT 1 FROM exhibition_genre g WHERE g.exhibition_id = e.id);

-- 3) 상세 미동기화 전시에 더미 detail 행 생성 (행 존재 = 동기화됨 의미라, 로컬 표시 목적으로만)
INSERT INTO exhibition_detail (exhibition_id, price, description, img_url, synced_at)
SELECT e.id,
       ELT(1 + MOD(e.id, 3), '무료', '성인 5,000원', '성인 10,000원 / 학생 5,000원'),
       CONCAT('[로컬 더미] ', e.title, ' — 실제 소개문은 상세 동기화 시 채워집니다.'),
       NULL, NOW(6)
FROM exhibitions e
WHERE NOT EXISTS (SELECT 1 FROM exhibition_detail d WHERE d.exhibition_id = e.id);

-- 4) 기존 detail 행의 결측 값 채움
UPDATE exhibition_detail d
JOIN exhibitions e ON e.id = d.exhibition_id
SET d.description = CONCAT('[로컬 더미] ', e.title, ' — 실제 소개문은 상세 동기화 시 채워집니다.')
WHERE d.description IS NULL;

UPDATE exhibition_detail
SET price = ELT(1 + MOD(exhibition_id, 3), '무료', '성인 5,000원', '성인 10,000원 / 학생 5,000원')
WHERE price IS NULL;
