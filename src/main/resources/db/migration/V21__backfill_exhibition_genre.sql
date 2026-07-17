-- 전시 초기화 파이프라인 3계층 이관(최종ERD 3장) — 2단계-b: 장르 읽기 전환의 선결 백필.
-- exhibitions.genre_keyword에 값이 있는데 정준층(exhibition_genre)에 대응 행이 없는 전시를 채운다.
-- 2단계-a에서 쓰기 이중화가 켜졌지만 그건 "앞으로 분류될 것"만 덮는다 — 그 전에 이미 분류된 행(운영 ~280건)은
-- 정준층에 없다. 대상 선별이 genre_keyword IS NULL이라 백필 배치의 대상에서도 빠져 영영 채워지지 않는다.
--
-- 왜 읽기 전환보다 먼저 해야 하나 — 이 백필이 없으면 전환 즉시 두 가지가 동시에 터진다:
--   1) DetailResponse.keywords가 기존 전시 전량에서 조용히 빈 배열이 된다(200은 그대로 나가고 로그도 없다).
--   2) 대상 선별이 "exhibition_genre 행 없음"으로 바뀌므로 기존 전시 전량이 재분류 대상이 되어, 운영에서
--      Gemini 실호출이 대량 발생한다. 비용과 한도(429)를 실제로 태우는 (2)가 (1)보다 크다.
--
-- 왜 provider='UNKNOWN'인가 — ⚠️ 이 값을 "분류 실패"로 읽지 마라. 분류는 됐고, 누가 했는지의 기록이 없을 뿐이다.
--   이 값들엔 출처 기록이 애초에 없다(V11이 genre_keyword 컬럼만 추가했고 계보 컬럼이 없었다).
--   'GEMINI'로 적으면 랜덤 폴백분까지 AI 분류로 둔갑해, provider가 풀려던 "폴백 영구 이탈"이 오히려 영구 봉인된다.
--   'RANDOM'으로 적으면 진짜 AI 분류분까지 재분류 대상이 되어 호출을 낭비한다. 둘 다 없는 사실을 지어내는 것이다.
--   UNKNOWN은 "이관 전에 이미 있던 값 — 출처를 모른다"를 정직하게 남겨, 재분류 여부를 나중에 데이터를 보고 정하게 한다.
--
-- 왜 classified_at을 NULL로 두나 — 분류 시각을 아는 컬럼이 없다. exhibitions.updated_at은 조회수 증가·상세
--   지연수집·영업시간 보강이 전부 갱신하므로 "장르를 언제 분류했나"와 무관하다. 넣으면 틀린 값을 정확한 값처럼
--   보이게 한다(전시 1건 조회만으로 "방금 분류함"이 된다). NULL = "모른다"로 provider='UNKNOWN'과 같은 말을 한다.
--
-- 멱등하다(NOT EXISTS) — 재실행돼도 이미 있는 행은 건드리지 않는다. 쓰기 이중화가 그 사이 채운 행도 보존된다.
--   uk_exhibition_genre_exhibition_id가 최후 방어선이다.
-- soft-delete된 전시도 포함한다 — 정준층은 살아있는 행의 뷰가 아니고(읽기 경로가 각자 deleted_at을 거른다),
--   복원(restore) 시 장르가 사라지지 않는 편이 안전하다. 대상 선별은 별도로 deleted_at IS NULL을 걸어 무관하다.
insert into exhibition_genre (exhibition_id, genre_keyword, provider, model, classified_at)
select e.id, e.genre_keyword, 'UNKNOWN', null, null
from exhibitions e
where e.genre_keyword is not null
  and trim(e.genre_keyword) <> ''
  and not exists (select 1 from exhibition_genre g where g.exhibition_id = e.id);
