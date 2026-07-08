-- 전시 장르 키워드(회화·드로잉/사진/미디어아트 등 마스터 중 1개). 분류기(랜덤 또는 AI-Gemini)가 부여한다.
-- CUSTOM은 등록 시, CATALOG(공공데이터)는 부팅 초기화 백필로 채운다. 미분류(백필 전/폴백 전)는 null.
alter table exhibitions
    add column genre_keyword varchar(50) null;
