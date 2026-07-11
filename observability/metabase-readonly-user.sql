-- Metabase(사용자 지표)가 앱 DB를 조회할 읽기 전용 계정.
-- 분석 도구가 운영 데이터를 실수로 변경하지 못하게 SELECT 권한만 준다.
-- 실행(로컬):  docker compose --profile app exec -T mysql mysql -uroot -p'verysecret' < observability/metabase-readonly-user.sql
-- 운영: 비밀번호를 강한 값으로 바꿔 1회 실행하고, Metabase 연결에 이 계정을 쓴다(host=mysql, port=3306, db=mydatabase).
CREATE USER IF NOT EXISTS 'metabase_ro'@'%' IDENTIFIED BY 'CHANGE_ME_metabase_ro';
GRANT SELECT ON mydatabase.* TO 'metabase_ro'@'%';
FLUSH PRIVILEGES;
