-- 전시 초기화 파이프라인 3계층 이관(최종ERD 3장) — 4단계-b: 정준층(place_hours) 백필. 읽기 전환의 선결 조건이다.
--
-- 왜 읽기 전환보다 먼저인가 — 쓰기 이중화는 "앞으로 조회될 장소"만 덮는다. 이미 영업시간이 채워진 장소는
--   대상 선별이 (operating_hours_synced_at is null or < staleBefore)라 최대 30일(refresh-after-days) 동안
--   재조회 대상이 아니다. 그 사이 읽기를 전환하면 **DetailResponse.operatingHours가 조용히 null이 된다**
--   (200은 그대로 나가고 로그도 없다). 값이 안 나오면 그게 곧 회귀다.
--
-- 왜 operating_hours가 **있는** 행만 넣나 — 없는 행(NULL)은 place_hours 행을 안 만들어도 읽기 결과가 지금과 같다(null).
--   반대로 행을 만들려면 상태를 정해야 하는데, 현행 스키마로는 "장소를 못 찾음(NOT_FOUND)"과 "찾았지만 영업시간
--   정보가 없음(NO_HOURS)"이 **구분되지 않는다** — 둘 다 formatted=null·synced_at만 남기기 때문이다.
--   (이 구분 불가야말로 place_hours.status가 앞으로 푸는 문제다.) 모르는 걸 둘 중 하나로 찍으면 없는 사실을
--   지어내는 것이고, 지어낸 값은 나중에 선별 재조회의 근거로 쓰여 조용히 틀린 결정을 만든다. 그래서 안 만든다.
--
-- 왜 provider='UNKNOWN'인가 — ⚠️ "조회 실패"로 읽지 마라. 조회는 됐고, **누가 줬는지의 기록이 없을 뿐**이다.
--   V19의 google_place_hours가 source(GOOGLE|MOCK)를 갖고 있었지만 **매 실행 전멸하는 스테이징**이라 과거 이력이 없다.
--   'GOOGLE'로 적으면 mock으로 채워진 값(로컬·develop 기본)까지 실호출 결과로 둔갑하고, 'MOCK'으로 적으면 그 반대다.
--   둘 다 없는 사실을 지어내는 것이라, 장르 백필(V21)과 같은 판단으로 UNKNOWN을 남긴다.
--
-- 왜 attempt_count=0인가 — 이 컬럼은 "우리가 센 시도 횟수"다. 이관 전 시도는 센 적이 없으므로 0이 정직하다
--   (0이 "시도해도 안 됐다"는 뜻이 아니다 — status가 SUCCEEDED다). provider='UNKNOWN'과 같은 말을 한다.
--
-- 멱등하다(NOT EXISTS) — 재실행돼도 이미 있는 행은 건드리지 않는다. 쓰기 이중화가 그 사이 채운 행도 보존된다.
--   uk_place_hours_place_key가 최후 방어선이다.
-- 같은 place_key를 공유하는 전시가 여럿이라 group by로 장소당 1행을 만든다. 같은 장소는 같은 조회 결과를 반영받으므로
--   값이 같은 게 정상이고, 조회 시점이 갈려 다를 수 있는 경우를 대비해 max()로 결정적으로 하나를 고른다.
-- soft-delete된 전시도 포함한다 — 정준층은 살아있는 행의 뷰가 아니고, place_hours는 전시가 아니라 **장소**의 속성이라
--   그 장소의 다른(살아있는) 전시가 같은 값을 읽는다.
insert into place_hours (place_key, formatted, status, provider, attempt_count, next_attempt_at)
select e.place_key, max(e.operating_hours), 'SUCCEEDED', 'UNKNOWN', 0, null
from exhibitions e
where e.place_key is not null
  and e.operating_hours is not null
  and trim(e.operating_hours) <> ''
  and not exists (select 1 from place_hours p where p.place_key = e.place_key)
group by e.place_key;
