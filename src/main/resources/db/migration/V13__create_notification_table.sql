-- 알림(09_알림.md). 사용자별 알림 한 건당 한 행. REMIND=리마인드(target_id=remind_id) · NOTICE=공지(target_id=null).
-- 읽음 여부는 예약어 회피를 위해 is_read 컬럼으로 둔다. 목록은 (user_id, created_at) 최신순 커서 페이지네이션.
create table notifications (
    id bigint not null auto_increment,
    user_id bigint not null,
    type varchar(20) not null,
    title varchar(255) not null,
    body varchar(1000) null,
    target_id bigint null,
    is_read boolean not null default false,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    deleted_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

-- 사용자별 최신순 목록 조회용.
create index idx_notifications_user_created on notifications (user_id, created_at);
