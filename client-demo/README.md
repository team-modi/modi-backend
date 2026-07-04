# Modi 기록 작성 데모 클라이언트

모디 백엔드의 **전시 수집(공공데이터) + 직접 작성 기록 플로우**가 실제로 동작하는지 눈으로 확인하기 위한 더미 클라이언트다. 프레임워크 없는 단일 HTML/JS.

## 전제
- 백엔드가 `http://localhost:18080`에서 실행 중이어야 한다.
  ```
  docker compose --profile app up --build -d   # 프로젝트 루트에서
  ```
- 이 클라이언트는 **반드시 `http://localhost:3000`** 에서 열어야 한다. 백엔드 CORS 허용 오리진이 `localhost:3000`이기 때문(파일 `file://` 직접 열기는 CORS 차단됨).

## 실행
```
cd client-demo
python3 -m http.server 3000
# 브라우저에서 http://localhost:3000 접속
```

## 플로우 (와이어프레임: 직접 작성)
1. **게스트 로그인** — 소셜 없이 토큰 발급(`POST /api/v1/auth/guest`)
2. **전시 선택** — 공공데이터로 수집된 실제 전시 목록(`GET /api/v1/exhibitions`), 검색/페이지네이션
3. **기록 작성** — 관람일 · 감정 키워드(프리셋 + 나만의 키워드) · 미디어(URL, 최대 5) · 감상 300자
4. **작성 완료** — `POST /api/v1/records`(writeMode=DIRECT) 저장 → 저장된 기록 상세 표시("기록이 저장되었어요")

상단 하단의 `API` 입력값으로 백엔드 주소를 바꿀 수 있다(기본 `http://localhost:18080`).

## 참고
- 현재 백엔드 계약 기준으로 동작한다(`emotionCodes`, AI 필드는 미전송). 기록 작성 API 재정리(P0: `emotionKeywords`·본문 300자·미디어 5·AI 필드 제거)가 반영되면 이 클라이언트도 함께 갱신한다. 상세 계획은 [../docs/record-writing-redesign-plan.md](../docs/record-writing-redesign-plan.md).
- "질문으로 작성"(AI 감상문) 플로우는 P1(AI 에이전트)에서 추가된다.
