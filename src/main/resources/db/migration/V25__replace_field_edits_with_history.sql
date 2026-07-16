-- 전시 초기화 파이프라인 3계층 이관(최종ERD 3장) — 6단계: 사람 수정 이력.
--
-- ⚠️ ERD 3장은 exhibition_field_edits(UK(exhibition_id, field_name), 사람 수정 보호)를 뒀지만,
--    2026-07-16 사용자 결정으로 exhibition_history(변경 이력, 감사)로 방향을 바꾼다. 왜:
--    · field_edits는 "이 필드는 사람 소유"라는 보호 가드다 — 필드당 1행(최신 상태만). 한 번의 수정이 여러 필드를
--      바꿔도 필드별 N행으로 쪼개져 "한 액션이었다"는 묶음이 사라진다.
--    · exhibition_history는 변경 이력이다 — 한 수정에 여러 필드가 바뀌면 같은 edited_at으로 묶여 이벤트가 보존되고,
--      old_value → new_value로 "무엇이 어떻게 바뀌었나"까지 남는다(감사).
--    · field_edits가 막으려던 "재수집 덮어쓰기"는 현재 일어나지 않는다(syncCatalog는 기존 목록 필드를 갱신하지 않음).
--      즉 보호는 아직 가상의 필요이고, 실재하는 필요는 "외부 수정 작업의 감사"다. 필요가 증명된 곳에만 짓는다(문서 2.2.2).
--
-- field_edits를 drop하는 이유: V20이 만들었지만 배선된 코드가 0줄인 죽은 테이블이다. 남겨두면 "왜 안 쓰지"의 함정이 된다.
--   (V20은 수정하지 않는다 — 마이그레이션은 append-only로 다룬다. 방향을 바꾼 사실이 이 파일에 남는 편이 정직하다.)
drop table if exists exhibition_field_edits;

-- 사람이 전시 필드를 수정한 이력(append-only). 행 = 필드 변경 1건.
--   한 번의 수정이 여러 필드를 바꾸면 같은 edited_at을 가진 여러 행이 된다(= 수정 이벤트 묶음키).
-- edited_by(누가)는 두지 않는다 — 외부에서 수정 작업이 이뤄지는 운영 특성상 주체 식별이 의미가 없다(2026-07-16 결정).
-- old_value/new_value는 강타입 text다(JSON blob ❌ — 문서 2.2.2가 경계). description처럼 긴 값도 담아야 해 varchar 아닌 text.
-- FK를 건다 — 전시가 소유한 자식이라 전시가 사라지면 의미가 없다(정준·벤더층과 달리 재생성 대상이 아니다).
--   같은 이유로 V1(record_keywords)·V10(remind_emotions)도 부모에 FK를 건다.
-- UK를 두지 않는다 — 이력이라 같은 필드의 여러 번 수정이 각각 새 행으로 쌓인다(field_edits와 정반대).
create table exhibition_history (
    id bigint not null auto_increment,
    exhibition_id bigint not null,
    field_name varchar(50) not null,
    old_value text null,
    new_value text null,
    edited_at datetime(6) null,
    primary key (id),
    constraint fk_exhibition_history_exhibition foreign key (exhibition_id) references exhibitions (id)
) engine=InnoDB;

-- "이 전시의 수정 내역을 시간순으로" 조회용(가장 흔한 질의).
create index idx_exhibition_history_exhibition_edited on exhibition_history (exhibition_id, edited_at);
