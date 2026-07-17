-- 전시 서빙 도메인 ERD 이행 — 3단계: 제약 강화(NOT NULL·인덱스·UK). 백필(V31)이 전부 채운 뒤라야 안전하다.
--
-- exhibition_place_id NOT NULL(ADR-06): place NULL=0(실측)이라 모든 전시가 전시장을 갖는다 — 백필이 센티넬까지 포함해 전량 채웠다.
-- 매칭 안 된 정준·벤더 행(그 주소의 전시가 사라진 잔여 행)은 조인 근거가 없어 제거한다 — place_key 제거 후엔 참조가 불가능해진다.

-- 전시 N:1 참조 확정 + 조인 인덱스(설계 §3).
alter table exhibitions
    modify column exhibition_place_id bigint not null;

create index idx_exhibitions_exhibition_place_id on exhibitions (exhibition_place_id);

-- 정준·벤더의 전시장 조인 키 확정. 매칭 실패 잔여 행 제거 후 NOT NULL + 장소당 1행(UK).
delete from place_hours where exhibition_place_id is null;
delete from google_place_response where exhibition_place_id is null;

alter table place_hours
    modify column exhibition_place_id bigint not null;
alter table google_place_response
    modify column exhibition_place_id bigint not null;

create unique index uk_place_hours_exhibition_place_id on place_hours (exhibition_place_id);
create unique index uk_google_place_response_exhibition_place_id
    on google_place_response (exhibition_place_id);

-- 재검증 최소간격·대상 선별이 synced_at으로 스캔하므로 인덱스(설계 §3 place_hours(synced_at)).
create index idx_place_hours_synced_at on place_hours (synced_at);
