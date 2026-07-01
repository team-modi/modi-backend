-- 유저 프로필 확장(02_유저.md): 프로필 이미지·연령대·거주지역.
-- age_group은 미입력/선택 안 함을 UNSPECIFIED로 저장(not null, 기본값). 나머지는 선택 입력이라 nullable.

alter table users
    add column profile_image_url varchar(255) null,
    add column age_group varchar(20) not null default 'UNSPECIFIED',
    add column residence_region varchar(20) null,
    add column residence_district varchar(50) null;
