-- ExhibitionDraft 스테이징 신설(ADR-10 2부) — 초기화 in-flight 상태의 단독 보유자.
--
-- 유효성 그라디언트(ADR-02): 벤더(뭐든 허용) → draft(불완전 허용 — 이 테이블) → 도메인(완전).
-- 목록분·상세분·장르분이 스텝별로 nullable 컬럼에 쌓이고, 승격 게이트(목록 코어 + 상세 스텝 해소 + 장르 필수)를
-- 채운 순간에만 한 트랜잭션에서 진짜 exhibitions로 승격된다 — 도메인은 완성분만 받는다.
--
-- 기존 전시는 백필하지 않는다: 이미 승격된(레거시) 전시의 뒤채움은 기존 경로가 유지되고,
-- draft는 신규 유입에만 적용된다(핸들러가 draft 우선·전시 폴백으로 이원화).

create table exhibition_draft (
    id bigint not null auto_increment,
    external_id varchar(255) not null,
    status varchar(20) not null,
    -- 목록분(스테이징 시 확정, 재sync 갱신)
    title varchar(500) not null,
    place_name varchar(500) null,
    region varchar(30) null,
    sigungu varchar(100) null,
    gps_x double null,
    gps_y double null,
    start_date date null,
    end_date date null,
    category varchar(30) null,
    poster_url varchar(1000) null,
    detail_url varchar(1000) null,
    service_name varchar(255) null,
    realm_name varchar(255) null,
    -- 상세분(FETCH_DETAIL 해소 시 — detail_resolved_at은 값 도착·무상세 확인을 구분하지 않는 "스텝 해소" 시각)
    price varchar(500) null,
    description text null,
    img_url varchar(1000) null,
    place_addr varchar(500) null,
    place_phone varchar(100) null,
    place_url varchar(1000) null,
    detail_resolved_at datetime(6) null,
    -- 장르분(CLASSIFY_GENRE 해소 시)
    genre_keyword varchar(50) null,
    genre_provider varchar(20) null,
    genre_model varchar(100) null,
    genre_classified_at datetime(6) null,
    -- 종료·추적
    promoted_exhibition_id bigint null,
    last_error text null,
    version bigint not null default 0,
    completed_at datetime(6) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

-- 스테이징 멱등 — 같은 원천이 재sync돼도 draft는 1행(갱신만).
create unique index uk_exhibition_draft_external on exhibition_draft (external_id);
-- 운영 감시(FAILED 누적)·상태별 조회.
create index idx_exhibition_draft_status on exhibition_draft (status);
