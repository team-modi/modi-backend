-- 전시 초기화 파이프라인 3계층 이관(최종ERD 3장) — 1단계: 신규 테이블 생성만.
-- 기존 컬럼(exhibitions.genre_keyword·operating_hours·detail_synced_at 등)은 그대로 둔다. 이 시점엔 어떤 동작도
-- 바뀌지 않는다 — 뒤 단계에서 쓰기 이중화 → 읽기 전환 → 컬럼 정리 순으로 옮긴다(각 단계가 되돌릴 수 있는 지점).
--
-- 층 구조: 벤더(원본·벤더 종속) → 정준(벤더 무관) → 도메인. 층은 필요가 증명된 곳에만 깐다:
--   · 카탈로그 축엔 정준 중간 테이블을 두지 않는다 — 단일 원천이라 exhibitions가 곧 정준(중간을 넣으면 순수 패스스루).
--   · AI 축엔 벤더 response 테이블을 두지 않는다 — 요청에서 구조화 출력(enum 스키마)으로 응답을 우리 어휘로 미리
--     강제해 변환이 무손실이라, 원본을 남기면 정준과 같은 내용의 복사본이 될 뿐이다.
-- 이 비대칭(원본 2개·정준 2개·둘 다 없는 축)은 "손실적 변환만 원본을 보존한다"는 한 규칙을 세 축에 똑같이 적용한 결과다.


-- ── 벤더층: 원본 보존 · 벤더 종속 ──────────────────────────────────────────────

-- 목록(realm2) 응답을 "아이템 단위"로 보존한다(페이지 단위 ❌ — 페이지는 페이지네이션의 부산물이지 의미 단위가 아니다).
-- 행 = 목록 응답 중 전시 1건의 조각. UK(external_id)라 재수집이 no-op가 되어 크기가 원천(280건 수준)에 수렴한다.
-- payload_hash: 원천이 값을 정정했는지 행 단위로 감지한다(페이지를 다시 파싱해 비교할 필요가 없다).
-- last_seen_at: 이번 동기화에도 원천에 있었는지 — 원천에서 사라진 항목 판별용.
create table culture_list_response (
    id bigint not null auto_increment,
    external_id varchar(100) not null,
    payload text null,
    payload_hash char(64) null,
    first_seen_at datetime(6) null,
    last_seen_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

create unique index uk_culture_list_response_external_id on culture_list_response (external_id);

-- 상세(detail2) 응답 원본 + 수집 상태기계. 단일 원천이라 이 자리가 곧 정준이다(별도 정준 테이블 ❌).
-- status가 푸는 문제: 현행은 applyDetail()·markDetailChecked()가 둘 다 detail_synced_at만 찍어
--   "상세를 채웠다"와 "원천에 상세가 없다"가 구분되지 않는다.
--   PENDING(미시도) · SUCCEEDED(상세 있음) · NO_DATA(원천에 상세 없음) · FAILED(일시 실패·재시도 대상) · EXHAUSTED(재시도 소진)
-- attempt_count·next_attempt_at: 영구 실패 행이 매 주기 무한 재시도되는 것을 막는다.
create table culture_detail_response (
    id bigint not null auto_increment,
    external_id varchar(100) not null,
    payload text null,
    status varchar(20) not null,
    attempt_count int not null default 0,
    next_attempt_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

create unique index uk_culture_detail_response_external_id on culture_detail_response (external_id);
-- 재시도 대상 선별(status + 도래 시각) 조회용.
create index idx_culture_detail_response_status_next on culture_detail_response (status, next_attempt_at);

-- 구글 Places(New) 응답 원본. periods JSON → "매일 10:00~18:00" 문자열 변환이 손실적(요일·구간 구조 전체를 잃음)이라
--   원본을 보존한다. 변환 규칙이 바뀌면 여기서 정준(place_hours)을 재생성한다(재파싱 경로).
-- V19의 google_place_hours(매 실행 전체 삭제되는 per-run 스테이징, 조회 경로 0개)를 대체한다.
--   그쪽은 UK가 없어 재수집이 누적됐지만, 여기는 UK(place_key)로 upsert 멱등이다.
-- place_key: 현재는 exhibitions.place_addr 원문 그대로다(2026-07-16 결정). 주소 정규화(중복 장소 수렴)는 이번 범위 밖 —
--   구조 이관과 동작 변경을 한 번에 하면 회귀 원인을 분리할 수 없기 때문이다. 이 컬럼이 나중에 정규화를 적용할 단일 지점이 된다.
-- 카카오 도입 시 kakao_place_response가 추가될 뿐, 정준·도메인·읽기 경로는 불변이다.
create table google_place_response (
    id bigint not null auto_increment,
    place_key varchar(500) not null,
    raw_json text null,
    fetched_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

create unique index uk_google_place_response_place_key on google_place_response (place_key);

-- 외부 호출 감사(append-only). 벤더·모델 불문 이 테이블 하나 — 벤더가 늘어도 스키마 변경이 없다.
--   api: CULTURE_LIST | CULTURE_DETAIL | GEMINI | CLAUDE | GOOGLE
--   model: AI 호출만 — 요청 모델(설정값). 실제 서빙 모델은 응답 modelVersion이라 정준(exhibition_genre.model)에 남긴다.
--   request_key: 호출 대상 식별(external_id·place_key 등). outcome: SUCCESS|NO_DATA|RATE_LIMITED|FAILED 등.
--   billable: 유료 호출 여부 — 비용 귀속용.
-- 목록 응답의 totalCount처럼 "아이템 밖 메타"는 아이템 단위 원본 테이블에 남길 자리가 없다. 그건 호출 이벤트의
--   속성이므로 이 테이블의 몫이다(현행은 totalCount를 파싱만 하고 버려 조용한 절단을 감지하지 못한다).
-- 멱등 대상이 아니다(호출은 이벤트) → UK 없음.
create table external_api_call (
    id bigint not null auto_increment,
    api varchar(30) not null,
    model varchar(50) null,
    request_key varchar(500) null,
    outcome varchar(20) not null,
    billable boolean not null default false,
    called_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

-- 감사 조회는 "어떤 API를 언제" 축으로 본다(비용 집계·호출량 추이).
create index idx_external_api_call_api_called on external_api_call (api, called_at);


-- ── 정준층: 벤더 무관 · 우리 도메인 어휘 ────────────────────────────────────────

-- 장르 분류 결과(정준). 벤더 원본 테이블이 없는 유일한 축이다 — 위 헤더의 무손실 판정 참조.
--   이 테이블이 유일한 저장소이고, 호출 기록은 external_api_call이 맡는다. 내일 Claude로 바꿔도 스키마 변경 0(provider 값만 는다).
-- provider(GEMINI|CLAUDE|RANDOM|USER)가 푸는 실제 문제: 현행은 출처를 안 남겨서, 랜덤 폴백값이 genre_keyword에
--   저장되면 IS NULL 대상에서 빠져 영구 이탈한다(AI가 분류한 것과 구분 불가). provider=RANDOM을 남기면 선별 재분류가 된다.
-- model: 같은 GEMINI라도 flash/pro는 분류 품질이 다르므로 "이 값을 어떤 모델이 만들었나"를 행 단위로 남긴다
--   (모델 업그레이드 시 구모델 산출분만 재분류). 기록 값은 응답의 modelVersion을 우선한다 — 요청 모델은 별칭(alias)일
--   수 있어 실제 서빙 모델과 다를 수 있고, 진실은 응답에 있다.
-- exhibitions와는 ID 참조다(FK ❌) — 정준층은 도메인과 생명주기가 다르고 원본에서 재생성될 수 있다.
create table exhibition_genre (
    id bigint not null auto_increment,
    exhibition_id bigint not null,
    genre_keyword varchar(50) null,
    provider varchar(20) not null,
    model varchar(50) null,
    classified_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

create unique index uk_exhibition_genre_exhibition_id on exhibition_genre (exhibition_id);

-- 영업시간 정준(벤더 무관 — 우리 표시 모델). 벤더 원본 JSON은 google_place_response에 남는다.
--   정준에 벤더 원본을 복사하지 않는다 — 그러면 정준이 원본의 복제가 되어 층의 의미가 죽는다.
-- status(SUCCEEDED|NOT_FOUND|NO_HOURS|FAILED): 성공/미발견은 벤더 무관 개념이라 정준에 둔다
--   (상세 축은 상태기계가 벤더 테이블에 있다 — 단일 원천이라 그 자리가 곧 정준이기 때문. 이 비대칭은 의도다).
-- attempt_count·next_attempt_at: 현행은 재시도 상태가 없어(exhibitions.operating_hours_synced_at 단일 컬럼이 전담)
--   영구 실패 장소가 매 주기 무한 재시도된다.
create table place_hours (
    id bigint not null auto_increment,
    place_key varchar(500) not null,
    formatted varchar(500) null,
    status varchar(20) not null,
    provider varchar(20) not null,
    attempt_count int not null default 0,
    next_attempt_at datetime(6) null,
    primary key (id)
) engine=InnoDB;

create unique index uk_place_hours_place_key on place_hours (place_key);


-- ── 도메인층 ──────────────────────────────────────────────────────────────────

-- 사람 수정 보호 — 재수집(동기화)이 사람이 고친 필드를 덮지 않게 한다.
-- UK(exhibition_id, field_name) = 전시의 한 필드당 수정 기록 1행.
-- 여기는 FK를 건다 — 전시가 소유한 자식이고 전시가 사라지면 의미가 없다(정준층과 달리 재생성 대상이 아니다).
--   같은 이유로 V1(record_keywords)·V10(remind_emotions)도 부모에 FK를 건다.
create table exhibition_field_edits (
    id bigint not null auto_increment,
    exhibition_id bigint not null,
    field_name varchar(50) not null,
    edited_by varchar(50) null,
    edited_at datetime(6) null,
    primary key (id),
    constraint fk_exhibition_field_edits_exhibition foreign key (exhibition_id) references exhibitions (id)
) engine=InnoDB;

create unique index uk_exhibition_field_edits_exhibition_field
    on exhibition_field_edits (exhibition_id, field_name);
