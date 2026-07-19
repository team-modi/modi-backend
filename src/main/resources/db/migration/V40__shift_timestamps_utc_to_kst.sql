-- 저장 시간대를 UTC → KST(Asia/Seoul)로 이관한다.
--
-- 지금까지 앱이 UTC로 기록해 왔다(컨테이너 JVM 기본 타임존 UTC + Hibernate가 ZonedDateTime을 UTC로 정규화).
-- 이 배포부터 앱이 KST로 기록하므로, 기존 행도 함께 옮기지 않으면 한 테이블에 UTC 행과 KST 행이
-- 섞여 정렬·기간조회가 전부 틀어진다.
--
-- 안전성:
--   * Flyway는 앱 기동 중 트래픽을 받기 전에 실행된다 → KST로 쓰는 앱이 살아있는 채로
--     마이그레이션을 기다리는 구간이 없다(구 컨테이너 종료 → 신 컨테이너가 이관 후 기동).
--   * 스키마의 datetime 컬럼을 information_schema로 실측해 명시 나열했다(51개).
--     flyway_schema_history.installed_on 은 의도적으로 제외한다(마이그레이션 실행 이력이라 이관 대상 아님).
--   * NULL + INTERVAL = NULL 이라 미삭제(deleted_at 등) 행은 그대로 NULL로 남는다.
--   * 되돌리려면 동일 컬럼에 `- INTERVAL 9 HOUR`. Flyway 버전 관리라 두 번 적용되지 않는다.
--
-- 날짜형(date) 컬럼(전시 startDate/endDate 등)은 시간대 개념이 없어 대상이 아니다.

update activity_logs set
    created_at = created_at + INTERVAL 9 HOUR;

update artists set
    created_at = created_at + INTERVAL 9 HOUR,
    deleted_at = deleted_at + INTERVAL 9 HOUR,
    updated_at = updated_at + INTERVAL 9 HOUR;

update culture_list_snapshot set
    first_seen_at = first_seen_at + INTERVAL 9 HOUR,
    last_seen_at = last_seen_at + INTERVAL 9 HOUR;

update exhibition_bookmarks set
    created_at = created_at + INTERVAL 9 HOUR,
    deleted_at = deleted_at + INTERVAL 9 HOUR,
    updated_at = updated_at + INTERVAL 9 HOUR;

update exhibition_detail set
    synced_at = synced_at + INTERVAL 9 HOUR;

update exhibition_draft set
    completed_at = completed_at + INTERVAL 9 HOUR,
    created_at = created_at + INTERVAL 9 HOUR,
    detail_resolved_at = detail_resolved_at + INTERVAL 9 HOUR,
    genre_classified_at = genre_classified_at + INTERVAL 9 HOUR,
    updated_at = updated_at + INTERVAL 9 HOUR;

update exhibition_genre set
    classified_at = classified_at + INTERVAL 9 HOUR;

update exhibition_history set
    edited_at = edited_at + INTERVAL 9 HOUR;

update exhibition_outbox set
    completed_at = completed_at + INTERVAL 9 HOUR,
    created_at = created_at + INTERVAL 9 HOUR,
    next_attempt_at = next_attempt_at + INTERVAL 9 HOUR,
    updated_at = updated_at + INTERVAL 9 HOUR;

update exhibition_place set
    created_at = created_at + INTERVAL 9 HOUR,
    deleted_at = deleted_at + INTERVAL 9 HOUR,
    updated_at = updated_at + INTERVAL 9 HOUR;

update exhibitions set
    created_at = created_at + INTERVAL 9 HOUR,
    deleted_at = deleted_at + INTERVAL 9 HOUR,
    updated_at = updated_at + INTERVAL 9 HOUR;

update external_api_call_log set
    called_at = called_at + INTERVAL 9 HOUR;

update google_place_snapshot set
    fetched_at = fetched_at + INTERVAL 9 HOUR;

update ingestion_run set
    finished_at = finished_at + INTERVAL 9 HOUR,
    started_at = started_at + INTERVAL 9 HOUR;

update notifications set
    created_at = created_at + INTERVAL 9 HOUR,
    deleted_at = deleted_at + INTERVAL 9 HOUR,
    updated_at = updated_at + INTERVAL 9 HOUR;

update place_hours set
    next_attempt_at = next_attempt_at + INTERVAL 9 HOUR,
    synced_at = synced_at + INTERVAL 9 HOUR;

update records set
    created_at = created_at + INTERVAL 9 HOUR,
    deleted_at = deleted_at + INTERVAL 9 HOUR,
    updated_at = updated_at + INTERVAL 9 HOUR;

update reminds set
    created_at = created_at + INTERVAL 9 HOUR,
    deleted_at = deleted_at + INTERVAL 9 HOUR,
    updated_at = updated_at + INTERVAL 9 HOUR;

update social_accounts set
    created_at = created_at + INTERVAL 9 HOUR,
    deleted_at = deleted_at + INTERVAL 9 HOUR,
    updated_at = updated_at + INTERVAL 9 HOUR;

update users set
    created_at = created_at + INTERVAL 9 HOUR,
    deleted_at = deleted_at + INTERVAL 9 HOUR,
    updated_at = updated_at + INTERVAL 9 HOUR;

update venues set
    created_at = created_at + INTERVAL 9 HOUR,
    deleted_at = deleted_at + INTERVAL 9 HOUR,
    updated_at = updated_at + INTERVAL 9 HOUR;
