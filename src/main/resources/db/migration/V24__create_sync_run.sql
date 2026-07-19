-- 전시 초기화 파이프라인 3계층 이관(최종ERD 3장) — 5단계: 동기화 실행 기록.
--
-- ⚠️ 최종ERD 3장에 없는 테이블이다(2026-07-16 사용자 결정으로 신설). 왜 external_api_call의 컬럼이 아니라 별도 테이블인가:
--   **절단은 "한 번의 동기화 실행"이 max-pages에서 멈춰버린 배치 단위 사실**이지, 개별 호출의 성패가 아니다.
--   목록 3콜은 각자 200 OK로 멀쩡히 성공하는데도 원천의 4번째 페이지를 안 부른 것이 절단이다 —
--   호출 행의 outcome으로는 표현할 자리가 없다(그 호출은 SUCCESS다).
--   external_api_call은 "무엇을 몇 번 불렀나 · 얼마를 태웠나"(append-only 감사), sync_run은 "이번 실행이
--   원천을 다 가져왔나"(배치 결과)로 역할이 갈린다.
--
-- 무엇을 푸나 — 현행은 응답의 totalCount를 파싱만 하고 **버린다**(CultureApiResponse.Body.totalCount 사용처 0곳).
--   그래서 `max-pages 5 × num-of-rows 100 = 500건`이라는 하드 상한을 넘는 순간 **아무 로그도 없이 조용히 절단**된다.
--   2026-07-15 실측 totalCount=280이라 지금은 여유가 있지만, 원천이 500을 넘기는 날 우리는 알 수 없다.
--   truncated=true 한 행이면 그날을 즉시 알 수 있다.
--
-- 집계 컬럼은 syncCatalog가 이미 계산해 로그로만 흘려보내던 값들이다("수집 N / 신규적재 N / 기존상세완성 N /
--   기간스킵 N / 실패연기 N"). 로그는 질의할 수 없어 추이·회귀를 볼 수 없다 — 같은 값을 행으로 남긴다.
-- append-only다(멱등 대상 아님 — 실행은 이벤트다) → UK 없음.
create table sync_run (
    id bigint not null auto_increment,
    started_at datetime(6) null,
    finished_at datetime(6) null,

    -- 원천이 말한 총 건수(응답의 totalCount). 인증키 미설정 등으로 호출 자체가 없었으면 null = "모른다".
    total_count int null,
    -- 절단 여부 — 원천에 더 있는데 상한(max-pages)에 걸려 못 가져왔나. 이 테이블의 존재 이유다.
    truncated boolean not null default false,

    -- 이하 syncCatalog의 실행 집계.
    collected int not null default 0,   -- 수집(적재 가능 필터 통과)
    inserted int not null default 0,    -- 신규 적재
    completed int not null default 0,   -- 기존 행 상세 완성
    skipped int not null default 0,     -- 기간 불량 스킵
    deferred int not null default 0,    -- 단건 실패 → 다음 주기 연기

    primary key (id)
) engine=InnoDB;

-- 추이 조회는 "언제" 축으로 본다(최근 실행부터 / 절단이 언제 시작됐나).
create index idx_sync_run_started_at on sync_run (started_at);
