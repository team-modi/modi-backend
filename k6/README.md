# k6 성능/검수 테스트

[k6](https://k6.io)로 API를 **E2E 검수**하고 **부하·한계**를 측정하는 스크립트 모음.

## 사전 준비
- k6 설치: `brew install k6` (v1.6+ 권장)
- 백엔드 실행: `docker compose --profile app up -d --build` → 기본 `http://localhost:18090`
  - 대상 주소는 모든 스크립트에서 `-e BASE_URL=...`로 바꿀 수 있다.

## 스크립트

### 1) `e2e.js` — 기능 E2E
전체 유저 여정을 한 번에 검증한다: 헬스 → 게스트 로그인 → 전시 목록 → 기록 생성(201)·상세·목록 → 리마인드 소환·저장·상세·목록 → 인증 가드(401).
```bash
k6 run k6/e2e.js
k6 run -e BASE_URL=http://localhost:18090 -e VUS=5 -e ITER=2 k6/e2e.js
```

### 2) `load.js` — 부하(mixed)
읽기(홈/브라우징) 램핑 + 쓰기(기록·리마인드 생성) 일정 부하를 **동시에** 건다.
```bash
k6 run k6/load.js
k6 run -e PEAK=100 -e WRITE_RPS=5 k6/load.js
```

### 3) `stress.js` — 한계점(용량 곡선)
읽기 부하를 계단식(`LEVELS`)으로 올리며 레벨별 p95/처리량을 측정해 knee를 찾는다.
```bash
k6 run --summary-export=stress.json k6/stress.js
k6 run -e LEVELS=50,100,200,400,700,1000 -e HOLD=20 k6/stress.js
```

## 참고 (동작 특성)
- **인증**: access 토큰은 **Bearer 헤더 + 쿠키** 양쪽으로 통한다. "토큰 없음" 검증 시 k6 쿠키 저장소를 비워야 진짜 401이 난다(`e2e.js` 반영).
- **리마인드 저장 AI**: `AI_API_KEY` 미설정이면 `aiStatus=SKIPPED`(best-effort) — 저장은 항상 성공하고 빠르다. 실 AI(`READY`)면 `POST /reminds`는 수 초가 걸려 **쓰기 용량이 외부 LLM에 종속**되므로 별도(낮은 동시성)로 측정할 것.
- **응답 형태**: 전시 목록은 커서 페이지네이션(`totalCount`/`nextCursor`), 기록·리마인드 목록은 오프셋(`totalElements`).

## 최근 측정 결과 (2026-07, 로컬 Docker: backend+mysql 단일 호스트)
- **E2E**: checks 100% (240/240), 0 errors.
- **부하(mixed, 읽기 100 VU + 쓰기 5/s)**: ~792 req/s, 에러 0%, 읽기 p95 166ms / 쓰기 p95 219ms.
- **한계(읽기 계단식)**: 처리량 천장 **≈ 900 req/s**, 1000 VU까지 **에러 0%**(우아한 지연 열화). 좋은 지연(p95<250ms) 운영점 ≈ **동시 100 VU**. 병목은 CPU(backend 피크 ~728%, mysql ~539%).
  - 로컬 단일 호스트 측정치 — 운영(인스턴스 분리)에선 재측정 필요.
