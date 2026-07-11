-- API 활동 로그: 사용자별 요청 활동을 추적한다(관리자 콘솔의 "실제 활동 기록").
--   인증된 요청만 인터셉터가 비동기로 기록한다(actuator/정적/admin 제외). append-only(soft-delete 없음).
--   보관 정책(요약/파티션/삭제)은 데이터가 쌓인 뒤 별도 과제로. 1GB 박스 부하 고려해 인덱스는 조회 축에만.
create table activity_logs (
    id bigint not null auto_increment,
    user_id bigint not null,
    method varchar(10) not null,
    path varchar(255) not null,
    status int not null,
    duration_ms bigint not null,
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create index idx_activity_logs_user_created on activity_logs (user_id, created_at);
create index idx_activity_logs_created on activity_logs (created_at);
