-- 전시 초기화 파이프라인 3계층 이관(최종ERD 3장) — 7단계: 이관 완료된 레거시 컬럼 정리(genre_keyword).
--
-- exhibitions.genre_keyword를 제거한다. 이 컬럼에 의존하던 모든 것이 정준층(exhibition_genre)으로 옮겨졌기 때문이다:
--   · 읽기(상세 keywords) → exhibition_genre (2단계-b)
--   · 대상 선별(미분류 백필) → findCatalogWithoutGenre의 `not exists (exhibition_genre)` (2단계-b)
--   · 백필(V21) → 이관 전 값을 provider=UNKNOWN으로 exhibition_genre에 이미 옮김
-- 즉 지금 이 컬럼은 쓰기 이중화만 남은 안전망이었고, 읽기 전환이 안정화됐으므로 제거한다.
--   "무엇이든 이 컬럼에 의존하던 것이 전부 정준층으로 갔는가"가 컬럼 제거의 조건이고, genre_keyword는 그 조건을 만족한다.
--
-- ⚠️ operating_hours·operating_hours_synced_at은 여기서 건드리지 않는다 — 아직 이관 전이다:
--   operating_hours_synced_at은 영업시간 보강 대상 선별(ExhibitionJpaRepository.findCatalogNeedingOperatingHours의
--   JPQL이 이 필드를 직접 읽는다)과 재조회 백오프를 여전히 담당하는 **라이브 메커니즘**이다. 이걸 지우려면 선별을
--   place_hours.status/next_attempt_at으로 옮기는 동작 변경(선별 전환)이 선행돼야 하는데, 그건 구조 이관이 아니라
--   별도 작업이다(구조 이관과 동작 변경을 섞지 않는다). operating_hours(값) 컬럼은 그 선별 전환과 한 쌍으로 정리한다.
--
-- 파생 쿼리 메서드명·JPQL 필드 참조는 컴파일러가 안 지켜준다 — 제거 전 grep -rni(대소문자 무시)로 전수 확인했고,
--   genreKeyword를 참조하는 파생 쿼리·JPQL은 없다(선별은 이미 not exists로 전환됨). 부팅 시 폭발 위험 없음.
alter table exhibitions
    drop column genre_keyword;
