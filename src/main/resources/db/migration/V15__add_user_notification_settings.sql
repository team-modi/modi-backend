-- 유저 알림 설정(02_유저 4.3·4.4): 리마인드·공지 수신 여부. 옵트아웃 방식이라 not null·기본 true.
alter table users
    add column remind_enabled boolean not null default true,
    add column notice_enabled boolean not null default true;
