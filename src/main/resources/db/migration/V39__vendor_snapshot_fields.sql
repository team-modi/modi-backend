-- ADR-13: 벤더 원본을 raw payload 대신 응답 구조 필드로 적재(ADR-01 폐기) + 수집 엔티티 리네임.
-- 값은 원문 verbatim(도메인 변환 이전) — 타입 정제·평문 추출은 도메인 몫이다.

-- 1) 테이블 리네임(엔티티명 정렬 — Snapshot/IngestionRun/ExternalApiCallLog)
rename table culture_list_response to culture_list_snapshot;
rename table culture_detail_response to culture_detail_snapshot;
rename table google_place_response to google_place_snapshot;
rename table sync_run to ingestion_run;
rename table external_api_call to external_api_call_log;

-- 2) 목록 스냅샷: realm2 응답 아이템 필드. payload 제거, 해시는 필드 정준 문자열 기반으로 재정의되므로
--    null 리셋 — 레거시 행은 다음 동기화가 "변경됨"으로 판정해 구조화 필드를 자동 재적재한다.
alter table culture_list_snapshot
    add column title        varchar(500)  null,
    add column start_date   varchar(20)   null,
    add column end_date     varchar(20)   null,
    add column place        varchar(300)  null,
    add column realm_name   varchar(100)  null,
    add column area         varchar(100)  null,
    add column sigungu      varchar(100)  null,
    add column thumbnail    varchar(1000) null,
    add column gps_x        varchar(50)   null,
    add column gps_y        varchar(50)   null,
    add column service_name varchar(200)  null,
    add column place_seq    varchar(50)   null;
alter table culture_list_snapshot drop column payload;
update culture_list_snapshot set payload_hash = null;

-- 3) 상세 스냅샷: detail2 응답 아이템 필드. 레거시 행은 null 잔존 허용(대상당 1회 조회 기록층 —
--    재적재 트리거가 없다. 값이 필요해지면 재조회가 채운다).
alter table culture_detail_snapshot
    add column title        varchar(500)  null,
    add column start_date   varchar(20)   null,
    add column end_date     varchar(20)   null,
    add column place        varchar(300)  null,
    add column realm_name   varchar(100)  null,
    add column area         varchar(100)  null,
    add column sigungu      varchar(100)  null,
    add column thumbnail    varchar(1000) null,
    add column gps_x        varchar(50)   null,
    add column gps_y        varchar(50)   null,
    add column service_name varchar(200)  null,
    add column price        text          null,
    add column contents     mediumtext    null,
    add column url          varchar(1000) null,
    add column phone        varchar(100)  null,
    add column img_url      varchar(1000) null,
    add column place_url    varchar(1000) null,
    add column place_addr   varchar(500)  null,
    add column place_seq    varchar(50)   null;
alter table culture_detail_snapshot drop column payload;

-- 4) 구글 장소 스냅샷: 응답 구조 필드 + 깊은 중첩(영업시간)은 구조 보존 JSON.
alter table google_place_snapshot
    add column place_id              varchar(200) null,
    add column display_name          varchar(300) null,
    add column formatted_address     varchar(500) null,
    add column regular_opening_hours json         null;
alter table google_place_snapshot drop column raw_json;
