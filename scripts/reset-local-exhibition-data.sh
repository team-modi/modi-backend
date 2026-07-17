#!/usr/bin/env bash
# 로컬 전시 데이터 초기화 — API 호출 없이 시드 스냅샷으로 채운다.
# 전제: modi-mysql 컨테이너 기동 + Flyway 적용된 스키마(앱 1회 부팅 or ./gradlew flywayMigrate)
set -euo pipefail
cd "$(dirname "$0")/.."
echo "[seed] modi-mysql/mydatabase 에 전시 시드 적용 중..."
docker exec -i modi-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" mydatabase' < scripts/seed-local-exhibitions.sql
echo "[seed] 완료. 검증:"
docker exec modi-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N mydatabase -e "SELECT CONCAT(\"exhibitions=\",COUNT(*)) FROM exhibitions; SELECT CONCAT(\"genre=\",COUNT(*)) FROM exhibition_genre; SELECT CONCAT(\"list_raw=\",COUNT(*)) FROM culture_list_response;"'
echo "[seed] 참고: CULTURE_API_KEY를 비워두면 부팅 동기화가 skip되어 시드가 유지된다."
