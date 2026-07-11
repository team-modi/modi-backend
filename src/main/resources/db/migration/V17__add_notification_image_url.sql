-- 알림 카드 좌측 썸네일용 이미지 URL. 생성 시점에 전시 포스터를 스냅샷(제목을 body에 박아두는 것과 동일 철학).
alter table notifications
    add column image_url varchar(2048) null;

-- 기존 행 백필: 이미 생성된 알림도 이미지가 뜨도록 target 전시/기록에서 포스터를 채운다.
--   EXHIBITION: target_id = 전시 id → 전시 포스터
update notifications n
    join exhibitions e on e.id = n.target_id
set n.image_url = e.poster_url
where n.type = 'EXHIBITION' and n.image_url is null;

--   REMIND: target_id = 기록 id → 기록에 스냅샷된 전시 포스터
update notifications n
    join records r on r.id = n.target_id
set n.image_url = r.exhibition_poster_url
where n.type = 'REMIND' and n.image_url is null;
