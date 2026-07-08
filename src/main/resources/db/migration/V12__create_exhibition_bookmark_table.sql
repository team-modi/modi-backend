-- 전시 북마크(관심 전시). 기존 record 북마크와 별개인 신규 도메인.
-- (user_id, exhibition_id) 한 쌍당 한 행. 해제는 soft-delete, 재등록은 같은 행 복원(멱등).
create table exhibition_bookmarks (
    id bigint not null auto_increment,
    user_id bigint not null,
    exhibition_id bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    deleted_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

-- 한 쌍당 한 행(멱등 토글의 기준). 활성/해제는 deleted_at으로 구분한다.
create unique index uk_exhibition_bookmarks_user_exhibition on exhibition_bookmarks (user_id, exhibition_id);
-- 사용자별 관심 목록·건수 조회용.
create index idx_exhibition_bookmarks_user on exhibition_bookmarks (user_id, deleted_at);
