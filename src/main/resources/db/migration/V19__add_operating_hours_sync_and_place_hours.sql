-- 전시 영업시간(운영시간) 기능. 구글 Places(New)로 place_addr를 조회해 영업시간을 채운다.
-- 원본(구글 응답 구조) = google_place_hours(매 실행 truncate 후 재적재되는 per-run 스테이징).
-- 표시값(우리 규칙 파생 문자열)은 기존 exhibitions.operating_hours 컬럼을 재사용한다(공공데이터엔 영업시간이 없어 항상 비어 있던 컬럼).
-- operating_hours_synced_at: 마지막 조회 시도 시각. 대상 선별(미조회·만료)과 실패 조회 백오프에 쓴다.

alter table exhibitions
    add column operating_hours_synced_at datetime(6) null;

-- 구글 Places(New) 응답 원본(장소당 1행). FK로 전시와 묶지 않는다(스테이징 + 매 실행 초기화).
create table google_place_hours (
    id bigint not null auto_increment,
    place_id varchar(256) null,
    display_name varchar(200) null,
    formatted_address varchar(500) null,
    queried_addr varchar(500) null,
    regular_opening_hours_json text null,
    source varchar(20) null,
    fetched_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

-- 조회에 쓴 구글 place_id 확인용(장소 중복 파악). 서로 다른 주소가 같은 place로 수렴할 수 있어 unique 아님.
create index idx_google_place_hours_place_id on google_place_hours (place_id);
