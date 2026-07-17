-- 전시 서빙 도메인 ERD 이행 — 5단계: 정준·벤더의 옛 조인 키(place_key = 주소) 제거. 조인이 exhibition_place_id로 완전히
-- 옮겨간 뒤의 정리다(V31 백필이 매핑에 place_key를 마지막으로 사용했다 — 그래서 여기서야 지운다).
--
-- 기존 place_key 컬럼 유지 여부 판단(근거): 유지하지 않는다. 전시장 자연키가 '정규화 이름'으로 바뀌면서 place_key(=주소)는
--   더는 어떤 조인의 키가 아니게 됐다. 이름과 주소는 값이 달라 "정렬"이 아니라 "대체"라서, 유지하면 죽은 컬럼이 조인 키인 척
--   남아 오독을 부른다. 백필을 마친 지금 제거가 마이그레이션 단순성·정합성 모두에 맞다.

drop index uk_place_hours_place_key on place_hours;
alter table place_hours drop column place_key;

drop index uk_google_place_response_place_key on google_place_response;
alter table google_place_response drop column place_key;
