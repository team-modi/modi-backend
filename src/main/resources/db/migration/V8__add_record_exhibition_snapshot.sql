alter table records
    add column exhibition_title      varchar(100)  not null,
    add column exhibition_type       varchar(20)   not null,
    add column exhibition_poster_url varchar(2048) null,
    add column exhibition_place      varchar(200)  null,
    add column exhibition_region     varchar(20)   null,
    add column exhibition_category   varchar(20)   null,
    add column exhibition_start_date date          null,
    add column exhibition_end_date   date          null;
