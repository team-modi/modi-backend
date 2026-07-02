-- API 응답 필드를 누락 없이 영속. 상세 지연수집 필드 + 우리 앱 조회수(인기순).
alter table exhibitions
    add column realm_name       varchar(50)   null,   -- realmName 원문(예 "전시")
    add column area_text        varchar(50)   null,   -- area 원문(region enum 파생 전 원본 보존)
    add column sigungu          varchar(50)   null,
    add column place_addr       varchar(500)  null,
    add column phone            varchar(100)  null,
    add column place_url        varchar(2048) null,
    add column img_url          varchar(2048) null,
    add column place_seq        varchar(100)  null,
    add column detail_synced_at datetime(6)   null,
    add column our_view_count   bigint not null default 0;
create index idx_exhibitions_view_count on exhibitions (our_view_count);
