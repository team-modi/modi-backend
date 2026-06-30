-- OAuth/JWT 인증 테이블. (User, SocialAccount 엔티티 — BaseEntity 공통 컬럼 포함)
-- main(소셜 로그인)이 ddl-auto=create-drop로 자동 생성하던 테이블을, develop의 flyway 기반에 맞춰 마이그레이션으로 옮긴다.

create table users (
    id bigint not null auto_increment,
    nickname varchar(255) not null,
    profile_completed bit not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    deleted_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

create table social_accounts (
    id bigint not null auto_increment,
    user_id bigint not null,
    provider varchar(255) not null,
    provider_user_id varchar(255) not null,
    email varchar(255) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    deleted_at datetime(6) null,
    primary key (id),
    constraint uq_provider_provider_user_id unique (provider, provider_user_id)
) engine=InnoDB;
