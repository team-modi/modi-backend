# Modi 기록 작성 데모 클라이언트

모디 백엔드의 **전시 수집(공공데이터) + 직접 작성 기록 플로우**가 실제로 동작하는지 눈으로 확인하기 위한 더미 클라이언트다. 프레임워크 없는 단일 HTML/JS.

## 전제
- 백엔드가 `http://localhost:18090`에서 실행 중이어야 한다.
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

## 플로우 (와이어프레임: 직접 작성 + 질문으로 작성)
1. **게스트 로그인** — 소셜 없이 토큰 발급(`POST /api/v1/auth/guest`)
2. **전시 선택** — 공공데이터로 수집된 실제 전시 목록(`GET /api/v1/exhibitions`), 검색/페이지네이션
3. **기록 작성** — 관람일 · 감정 키워드(프리셋 + 나만의 키워드) · 미디어(URL, 최대 5)
4. **작성 모드 선택** — "어떻게 기록할까요?"
   - **직접 작성(DIRECT)**: 감상 300자 → `POST /api/v1/records`(writeMode=DIRECT)
   - **질문으로 작성(AI)**: `POST /api/v1/records/ai/questions`로 전시 맥락 질문 3개 생성 → 답변 → `POST /api/v1/records/ai/compose`로 감상문 다듬기 → 수정/다시 다듬기 → `POST /api/v1/records`(writeMode=AI)
5. **작성 완료** — 저장된 기록 상세 표시("기록이 저장되었어요")

상단 하단의 `API` 입력값으로 백엔드 주소를 바꿀 수 있다(기본 `http://localhost:18090`).

## 참고
- **질문으로 작성(AI)** 은 서버에 `ANTHROPIC_API_KEY`가 설정돼 있어야 동작한다(실제 Claude 호출 — 수 초 소요). 미설정 시 503 AI_DISABLED. 직접 작성은 키 없이도 동작.
- 상세 계획은 [../docs/record-writing-redesign-plan.md](../docs/record-writing-redesign-plan.md).
