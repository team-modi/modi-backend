-- 전시 서빙 도메인 ERD 이행 — 4단계: exhibitions에서 이관된 컬럼 제거. 읽기 전환(코드)이 전부 새 출처를 보게 된 뒤의 최종 단계다.
--
-- 제거 대상과 새 자리:
--   · 장소 계열(place/place_addr/place_key/gps_x/gps_y/sigungu/area_text/realm_name/phone/place_url/place_seq) → exhibition_place
--   · 영업시간(operating_hours/operating_hours_synced_at) → place_hours
--   · 상세 계열(price/description/img_url/detail_synced_at) → exhibition_detail
--   · region → exhibition_place(장소의 속성)
--   · artist → artists + exhibition_artists(N:M)
-- 잔류(코어): type/external_id/owner_id/title/start_date/end_date/category/format/poster_url/detail_url/service_name/our_view_count
--   + exhibition_place_id. poster_url은 목록 thumbnail 소스라 코어 잔류(설계 §1 교정). detail_url·service_name은 CATALOG 목록
--   소스(생성 시점 확정)라 의도된 판별 null로 코어 잔류(ADR-02 — 부재는 타입으로; 지연 도착이 아니므로 exhibition_detail 아님).

alter table exhibitions
    drop column place,
    drop column place_addr,
    drop column place_key,
    drop column gps_x,
    drop column gps_y,
    drop column sigungu,
    drop column area_text,
    drop column realm_name,
    drop column phone,
    drop column place_url,
    drop column place_seq,
    drop column operating_hours,
    drop column operating_hours_synced_at,
    drop column region,
    drop column artist,
    drop column description,
    drop column price,
    drop column img_url,
    drop column detail_synced_at;
