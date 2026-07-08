-- 전시관(venue) 마스터(전시 5.5). 개인 전시 직접 등록 시 전시관명 자동완성(GET /venues)·venueId 선택에 쓰인다.
-- 지역은 전시와 같은 region 축(ExhibitionRegion)을 재사용한다. 조회는 살아있는 행만(deleted_at is null).

create table venues (
    id bigint not null auto_increment,
    name varchar(200) not null,
    address varchar(500) null,
    region varchar(20) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    deleted_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

-- 전시관명 자동완성(부분 일치) 조회용.
create index idx_venues_name on venues (name);

-- 초기 시드(자동완성 테스트/데모용). created_at/updated_at = 현재 시각.
insert into venues (name, address, region, created_at, updated_at) values
    ('아리랑 문화관', '서울 성북구 아리랑로 82', 'SEOUL', now(6), now(6)),
    ('아시아 현대미술관', '서울 종로구 삼청로 30', 'SEOUL', now(6), now(6)),
    ('아라리오뮤지엄 인 스페이스', '서울 종로구 율곡로 83', 'SEOUL', now(6), now(6)),
    ('국립현대미술관 서울', '서울 종로구 삼청로 30', 'SEOUL', now(6), now(6)),
    ('예술의전당 한가람미술관', '서울 서초구 남부순환로 2406', 'SEOUL', now(6), now(6)),
    ('세종문화회관 미술관', '서울 종로구 세종대로 175', 'SEOUL', now(6), now(6)),
    ('경기도미술관', '경기 안산시 단원구 동산로 268', 'GYEONGGI', now(6), now(6)),
    ('백남준아트센터', '경기 용인시 기흥구 백남준로 10', 'GYEONGGI', now(6), now(6)),
    ('부산시립미술관', '부산 해운대구 APEC로 58', 'BUSAN', now(6), now(6)),
    ('부산현대미술관', '부산 사하구 낙동남로 1191', 'BUSAN', now(6), now(6)),
    ('대구미술관', '대구 수성구 미술관로 40', 'DAEGU', now(6), now(6)),
    ('국립현대미술관 과천', '경기 과천시 광명로 313', 'GYEONGGI', now(6), now(6));
