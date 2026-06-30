# modi-backend

## Docker 실행 (스프링 + MySQL 한 번에)

```bash
git pull origin main
docker compose --profile app up -d --build   # 띄우기
docker compose --profile app down            # 끄기
```

> `--profile app` 없이 `docker compose up`만 하면 MySQL만 뜹니다.

- API: http://localhost:8080
- 헬스체크: http://localhost:8080/actuator/health
