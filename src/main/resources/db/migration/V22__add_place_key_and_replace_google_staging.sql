-- 전시 초기화 파이프라인 3계층 이관(최종ERD 3장) — 4단계-a: place_key 컬럼 + V19 스테이징 대체.
--
-- 1) exhibitions.place_key — 정준층(place_hours)과 전시를 잇는 조인 키.
--    1단계에서 일부러 안 만들었다: 그땐 "테이블 생성만"이 원칙이었고, place_key = raw place_addr인 지금은
--    이 컬럼이 place_addr의 정확한 복사본이라 조인이 실제로 필요해지는 이 단계에서 넣는 게 맞다.
--    ⚠️ 값은 place_addr 원문 그대로다(2026-07-16 결정). 주소 정규화(중복 장소 수렴)는 이번 범위 밖 —
--    구조 이관과 동작 변경을 한 번에 하면 회귀 원인을 분리할 수 없기 때문이다. 이관으로 place_key 산출 지점이
--    코드에서 한 곳(PlaceKey.of)으로 모이므로, 정규화는 나중에 그 함수 하나만 바꾸면 전 경로에 적용된다.
alter table exhibitions
    add column place_key varchar(500) null;

-- 기존 행 백필. place_addr가 없는 행(상세 미수집)은 place_key도 없다 — 조회 대상 선별이 placeAddr is not null이라 무관하다.
update exhibitions
set place_key = place_addr
where place_addr is not null;


-- 2) V19의 google_place_hours를 벤더층 google_place_response(V20)로 대체한다.
--
-- 지우는 게 안전한 이유: 이 테이블은 **SELECT 경로가 0개**다(어디서도 읽지 않는다). 게다가 영업시간 보강이
--   돌 때마다 전체 삭제 후 재적재되는 per-run 스테이징이라, 보존할 이력이라는 개념 자체가 없다.
--
-- 무엇이 좋아지나 — UK가 생긴다:
--   · 기존: UK 없음 → 재수집이 행을 누적시킨다. 그래서 "매 실행 전체 삭제"라는 대응이 필요했고,
--           그 reset이 대상 조회·조기 종료보다 먼저 도는 바람에 **할 일이 0건이어도 매일 스테이징이 전멸**했다.
--   · 지금: UK(place_key) → upsert가 멱등이다. 그래서 reset이라는 개념이 통째로 사라지고, 위 버그도 원인부터 없어진다.
--
-- 잃는 컬럼(place_id·display_name·formatted_address·source)은 사라지지 않는다 —
--   google_place_response.raw_json이 이제 regularOpeningHours만이 아니라 **구글 Place 응답 전체**를 담아
--   그 값들을 원본 그대로 보존한다(벤더 원본은 벤더 어휘 그대로 남긴다는 층 규칙). queried_addr는 place_key가 대신한다.
drop table if exists google_place_hours;
