-- 전시 서빙 도메인 ERD 이행 — 2단계: 백필. 기존 exhibitions 데이터를 새 구조로 이관한다(V21·V23 선례 — 백필은 마이그레이션 안에서).
-- 읽기 전환(전시장·상세·영업시간 출처 변경)은 코드가 맡고, 이 백필이 그 선결 조건이다(값이 안 나오면 그게 곧 회귀).
--
-- 정규화 규칙 = 자바 PlaceKey.of / Artist.normalize 와 일치해야 한다: TRIM 후 연속 공백 1개화.
--   REGEXP_REPLACE(TRIM(x), '[[:space:]]+', ' ') 로 표현한다(ICU POSIX 공백류 = \\s 근사).

-- 1) 전시장 upsert — 정규화 이름 기준 장소당 1행. 이름이 비면 센티넬('(장소 미상)')로 수렴(exhibition_place_id NOT NULL 지탱).
--    같은 이름의 여러 전시가 region/gps 등이 갈릴 수 있어 max()로 결정적으로 하나를 고른다(ADR-07: 공격적 병합 아님, 완전 동일 이름 수렴).
insert into exhibition_place (place_key, name, region, sigungu, gps_x, gps_y, address, phone, place_url,
    created_at, updated_at)
select
    regexp_replace(trim(coalesce(nullif(trim(e.place), ''), '(장소 미상)')), '[[:space:]]+', ' ') as pkey,
    regexp_replace(trim(coalesce(nullif(trim(e.place), ''), '(장소 미상)')), '[[:space:]]+', ' ') as pname,
    max(e.region), max(e.sigungu), max(e.gps_x), max(e.gps_y),
    max(e.place_addr), max(e.phone), max(e.place_url),
    now(6), now(6)
from exhibitions e
group by pkey
on duplicate key update updated_at = values(updated_at);

-- 2) 전시 → 전시장 참조 채움(정규화 이름으로 매칭).
update exhibitions e
join exhibition_place p
    on p.place_key = regexp_replace(trim(coalesce(nullif(trim(e.place), ''), '(장소 미상)')), '[[:space:]]+', ' ')
set e.exhibition_place_id = p.id
where e.exhibition_place_id is null;

-- 3) 상세 satellite 백필 — 상세 값이 하나라도 있거나 detail_synced_at이 있는 행. 동기화 시각은 detail_synced_at 우선, 없으면 updated_at.
--    멱등(NOT EXISTS) — 재실행/쓰기 이중화가 만든 행은 보존한다.
insert into exhibition_detail (exhibition_id, price, description, img_url, synced_at)
select e.id, e.price, e.description, e.img_url, coalesce(e.detail_synced_at, e.updated_at, now(6))
from exhibitions e
where (e.detail_synced_at is not null
        or (e.price is not null and trim(e.price) <> '')
        or (e.description is not null and trim(e.description) <> '')
        or (e.img_url is not null and trim(e.img_url) <> ''))
  and not exists (select 1 from exhibition_detail d where d.exhibition_id = e.id);

-- 4) 작가 백필 — 기존 artist 문자열(정규화)로 artists upsert + 조인 생성. 공공데이터엔 작가가 없어 CUSTOM 등록분만 채워진다.
insert into artists (name, created_at, updated_at)
select distinct regexp_replace(trim(e.artist), '[[:space:]]+', ' '), now(6), now(6)
from exhibitions e
where e.artist is not null and trim(e.artist) <> ''
  and not exists (
      select 1 from artists a where a.name = regexp_replace(trim(e.artist), '[[:space:]]+', ' '));

insert into exhibition_artists (exhibition_id, artist_id)
select e.id, a.id
from exhibitions e
join artists a on a.name = regexp_replace(trim(e.artist), '[[:space:]]+', ' ')
where e.artist is not null and trim(e.artist) <> ''
  and not exists (
      select 1 from exhibition_artists ea where ea.exhibition_id = e.id and ea.artist_id = a.id);

-- 5) 정준층(place_hours) 조인 키 이관 — 운영 DB엔 place_key(=주소) 기준 데이터가 있을 수 있다(핸드오프 주의).
--    place_key(주소) → 그 주소 전시의 exhibition_place_id 로 매핑. 같은 주소가 여러 전시장 이름에 걸리면 min으로 결정적 선택.
--    synced_at은 파이프라인 V27이 이미 백필했으므로 여기서 건드리지 않는다(재검증 최소간격 기준 = V27 소유).
update place_hours h
set h.exhibition_place_id = (
        select min(e.exhibition_place_id) from exhibitions e where e.place_key = h.place_key)
where h.exhibition_place_id is null
  and exists (select 1 from exhibitions e where e.place_key = h.place_key);

-- 6) 벤더층(google_place_response) 조인 키 이관 — 같은 매핑.
update google_place_response g
set g.exhibition_place_id = (
        select min(e.exhibition_place_id) from exhibitions e where e.place_key = g.place_key)
where g.exhibition_place_id is null
  and exists (select 1 from exhibitions e where e.place_key = g.place_key);
