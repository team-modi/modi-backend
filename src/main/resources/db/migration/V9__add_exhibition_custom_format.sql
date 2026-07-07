-- 개인 전시(CUSTOM) 직접 등록용 필드: 전시 형태(개인전/단체전/기획전/아트페어) + 참여 작가·주최명.
-- CATALOG(외부 수집) 전시는 null. 카테고리(회화·사진 등 매체)와는 별개 축.
alter table exhibitions
    add column format varchar(20) null,
    add column artist varchar(100) null;
