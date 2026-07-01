-- 유저 출생연도(02_유저.md 확장, 카카오 동의항목 birthyear). 소셜 미동의/미지원 시 null이라 nullable.
alter table users
    add column birth_year int null;
