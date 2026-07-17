-- 전시 서빙 도메인 ERD 이행(설계 §1 서빙 계열) — 1단계: 신설 테이블 생성 + 이관용 컬럼 추가(백필은 V31).
--
-- 근거(ADR):
--   · ADR-05/06/07 전시장(exhibition_place) 도입. 전시:전시장 = N:1, 자연키 = 정규화(trim·연속공백 1개화) place 이름.
--     지역(region)은 전시가 아니라 전시장의 속성이라 여기로 이동한다(전시에서는 V33에서 제거).
--   · ADR-03 상세(price/description/img)를 exhibition_detail(1:1 satellite)로 분리 — 연관 부재 = 미동기화.
--   · 작가는 별도 개념(향후 설명·부가 정보 예정)이라 artists(정규화 이름 UK) + exhibition_artists(N:M) 조인으로 분리.
--     공공데이터 Item엔 작가가 없어 기존 exhibitions.artist는 CUSTOM 등록분뿐이다(V31 백필).
--   · place_hours/google_place_response는 조인 키를 place_key(=주소) → exhibition_place_id로 바꾼다(자연키가 이름으로
--     정해지면서 장소당 1행이 전시장 행에 직접 정렬됨). 기존 place_key 컬럼은 V31 백필의 매핑 소스로 남겼다가 V34에서 제거한다.
--
-- ⚠️ V20 카탈로그 pass-through 전제 변경: 기존엔 목록 아이템의 장소/상세/작가 필드가 exhibitions 한 행에 그대로 실렸다.
--    이제 그 필드들은 생성 시점에 전시장으로 resolve-or-create되고 상세는 지연 도착 satellite로 분리된다.

-- 1) 전시장(도메인). 자연키 = 정규화 이름(place_key). 신원 필드는 목록 소스, 보강 필드(address/phone/place_url)는 상세 도착 시.
create table exhibition_place (
    id bigint not null auto_increment,
    place_key varchar(500) not null,
    name varchar(200) not null,
    region varchar(20) null,
    sigungu varchar(50) null,
    gps_x double null,
    gps_y double null,
    address varchar(500) null,
    phone varchar(100) null,
    place_url varchar(2048) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    deleted_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

create unique index uk_exhibition_place_place_key on exhibition_place (place_key);

-- 2) 전시 상세(1:1 satellite). 행 존재 = 상세 동기화됨. 값은 결측 잦아 nullable, synced_at은 행이 있으면 필수.
create table exhibition_detail (
    id bigint not null auto_increment,
    exhibition_id bigint not null,
    price varchar(500) null,
    description text null,
    img_url varchar(2048) null,
    synced_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create unique index uk_exhibition_detail_exhibition_id on exhibition_detail (exhibition_id);

-- 3) 작가(도메인). 자연키 = 정규화 이름. 지금은 이름만(YAGNI — 설명 등 부가 필드는 필요해질 때).
create table artists (
    id bigint not null auto_increment,
    name varchar(200) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    deleted_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

create unique index uk_artists_name on artists (name);

-- 4) 전시-작가 조인(N:M). exhibition_id·artist_id는 논리 참조(경계 넘는 FK 금지 — exhibition_genre 선례). UK로 조인 멱등.
create table exhibition_artists (
    id bigint not null auto_increment,
    exhibition_id bigint not null,
    artist_id bigint not null,
    primary key (id)
) engine=InnoDB;

create unique index uk_exhibition_artists on exhibition_artists (exhibition_id, artist_id);

-- 5) 이관용 컬럼 추가(백필 전까지 nullable). exhibitions는 N:1 참조 키, 정준·벤더는 전시장 조인 키.
alter table exhibitions
    add column exhibition_place_id bigint null;

alter table place_hours
    add column exhibition_place_id bigint null,
    add column synced_at datetime(6) null;

alter table google_place_response
    add column exhibition_place_id bigint null;
