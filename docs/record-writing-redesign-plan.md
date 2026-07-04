# 기록 작성 재설계 + AI 감상문 에이전트 개발 플랜

> 상태: 계획(진행 중) · 최종 업데이트 2026-07-05
> 근거: `04_전시기록`·`03_전시 기록 선택_B` 와이어프레임 / 사용자 결정사항
> 관련: 기존 [record-archive-development-plan.md](record-archive-development-plan.md)

## 1. 배경 / 목표

기록 작성 와이어프레임이 확정되어, 현재 백엔드를 그 방향으로 재정리한다.

- **현재 구조의 문제**: AI 필드(`aiSummary`·`aiKeywords`·`representativeEmotion`·`cardPhrase`)를 **클라이언트가 채워서 전송**한다. 실제 서버측 AI 응답 생성이 없다.
- **와이어프레임의 요구**: 사용자는 두 가지 모드로 기록한다. 그중 "질문으로 작성"은 **서버(AI)가 감상문 본문을 생성**해 준다.

목표는 두 축이다.
- **(P0) 기록 작성 API 재정리** — 와이어프레임의 입력 모델(관람일·감정 키워드·미디어·작성 모드)에 맞춤. AI 제외.
- **(P1) AI 감상문 멀티스텝 에이전트** — 전시 맥락 기반 질문 생성 + Q&A → 감상문 본문 생성.

## 2. 와이어프레임 요약

### 2.1 공통 메타 입력 (`04_전시기록`)
- 상단: 선택된 **전시 카드**(포스터·제목·작가·장소) = 전시 스냅샷.
- **관람일**(viewedAt): 날짜 피커(미래 불가).
- **감정 키워드**: 프리셋 칩(슬픈·강렬한·재미있는·유쾌한·서정적인·화나는·아름다운·관심있는 …) + **나만의 키워드**(커스텀, **10자 이내**, 복수 추가/삭제).
- **내가 바라본 전시**(미디어): 사진/영상, **최대 5개**(사진선택·영상선택 분리, 영상 썸네일에 재생 아이콘).

### 2.2 작성 모드 선택 (`03_전시 기록 선택_B` — "어떻게 기록할까요?")
- **직접 작성(DIRECT)**: "내 감상을 바로 글로 남겨요 / 빠르게 기록하고 싶은 분께".
- **질문으로 작성(AI)**: "질문에 답하면 AI가 감상문으로 정리해줘요 / 글 작성이 어려운 분께".

### 2.3 직접 작성 플로우 (DIRECT)
1. 모드 선택에서 "직접 작성" → **다음**
2. "전시에 대한 감상을 자유롭게 남겨보세요" — 본문 textarea **0/300** → **작성 완료**
3. "기록이 저장되었어요" — 기록 보러가기 / 홈으로 가기

### 2.4 질문으로 작성 플로우 (AI)
1. 모드 선택에서 "질문으로 작성" → **다음**
2. **Q1/Q2/Q3** (3단계, 상단 진행바, 각 답변 **0/300**, **"다른 질문 보기"** 로 질문 교체)
   - 예) Q1 "오늘 가장 시선이 오래 남은 장면은?" / Q2 "그 장면에서 어떤 감정?" / Q3 "이 전시를 한 문장으로?"
3. **"감상문으로 다듬기"** → **AI가 답변 기반 감상문 본문 생성**(로딩) → 결과 화면
4. 결과 화면 "AI가 당신의 답변을 바탕으로 감상문을 정리했어요" — 본문 편집 가능(**120/300**), **"다시 다듬기"**(재생성), **작성 완료**
5. "기록이 저장되었어요"

## 3. 확정된 결정사항

| 주제 | 결정 |
|---|---|
| AI 산출물 범위 | **감상문 본문(content)만**. 요약·대표감정·카드문구·아카이브 카드는 **이번 범위 밖(보류)** — 카드 디자인 미확정 |
| 감상문 생성 방식 | **동기·인터랙티브 compose 엔드포인트**(저장과 분리). 저장 시 비동기 PENDING→READY 아님 |
| 질문(Q1~Q3) 출처 | **전시 맥락 기반 AI 동적 생성**("다른 질문 보기"=재요청) |
| LLM | **provider 추상화**(교체 가능), 우선 **Claude**(`claude-sonnet-5`). OAuth의 `OAuthClient` 전략 패턴과 동일 컨벤션 |
| AI 형태 | **멀티스텝 에이전트**(전시 상세 조회 등 도구 활용) |
| 진행 | **P0 API 먼저 → P1 AI 나중**, 각각 별도 PR |
| 본문 길이 | **최대 300자**(와이어프레임 0/300 기준, 두 모드 공통) |

### 확정(구현 반영)
- **감정 필드**: 요청 필드명은 **`emotionCodes` 유지**(응답·클라·Postman churn 회피). 값은 프리셋+커스텀 통합 한글 라벨, 항목당 ≤10자(`@Size(max=10)`), 1개 이상 필수. 프리셋 목록은 FE 소유. 추후 프리셋/커스텀 구분(source)·취향 집계 필요 시 확장.
- **응답의 미사용 AI 필드**: 목록/상세 응답 스키마는 **그대로 유지**(FE 참조 가능성). 신규 기록은 `aiSummary`·`representativeEmotion`·`cardPhrase`=null, `aiKeywords`·`userKeywords`=빈 배열. 아카이브 카드 정식화 시 재설계.
- **본문 길이**: 요청 DTO `content` `@Size(max=300)`.
- **미디어**: `MAX_MEDIA_COUNT` 5.
- **요청에서 제거**: `userKeywords`·`aiKeywords`·`aiSummary`·`representativeEmotion`·`cardPhrase`. 저장 시 AI 텍스트 필드 null, `aiStatus`=READY(내부 고정, content가 최종본).
- **엔티티**: `Record.create`/`replaceContent` 시그니처는 유지(도메인 테스트 churn 회피), 서비스가 null·READY 전달.

## 4. P0 — 기록 작성 API 재정리 (AI 제외)

### 4.1 데이터 모델 변경 (구현 완료)
- `RecordService.MAX_MEDIA_COUNT`: **3 → 5**.
- 감정: 요청 필드명 **`emotionCodes` 유지**(값은 프리셋+커스텀 한글 라벨, 각 ≤10자, 1개 이상 필수). 저장은 기존 `RecordEmotion`(문자열) 재사용.
- 본문 `content`: `@Size(max = 300)`(기존 5000 → 300).
- 요청에서 제거: `aiSummary`, `aiKeywords`, `representativeEmotion`, `cardPhrase`, `userKeywords`(`aiStatus`도 요청 미노출).
  - DB 컬럼은 **물리적으로 유지**(파괴적 마이그레이션 회피). 생성/수정 시 AI 텍스트 필드 null, `aiStatus`는 not-null이라 `READY`로 세팅.
- `writeMode`(DIRECT|AI) 유지 — 어느 모드로 작성했는지 보존.

### 4.2 API 계약
- 저장은 **작성 완료 시 단일 `POST /api/v1/records`**(다단계 화면은 FE 상태, 드래프트 API 불필요).
- 요청(잠정):
  ```json
  {
    "exhibitionId": 51,
    "writeMode": "DIRECT",         // DIRECT | AI
    "viewedAt": "2026-07-01",
    "content": "전시에 대한 자유 감상 (<=300자)",
    "emotionCodes": ["강렬한", "재미있는", "흥미로운"],
    "media": [
      { "type": "PHOTO", "url": "https://.../1.jpg", "sortOrder": 0, "sizeBytes": 1048576 }
    ]
  }
  ```
- 검증: 감정 1개↑·항목 ≤10자 / 미디어 ≤5·사진10MB·영상100MB / 관람일 미래 불가 / content ≤300.
- 응답: 기존 `RecordDetailResponse` 유지(미사용 AI 필드는 null).

### 4.3 작업 항목
1. `RecordCreateRequest`/`RecordUpdateRequest` DTO 재정리.
2. `RecordService` 매핑·검증 수정(미디어 5, 감정 라벨, content 300).
3. 응답 DTO 정리(미사용 AI 필드 null 유지).
4. 테스트 갱신 + 신규(미디어 5 경계, 커스텀 감정 라벨, content 300 경계).
5. Postman 컬렉션 Record 폴더 갱신.

## 5. P1 — AI 감상문 멀티스텝 에이전트 (다음 PR)

### 5.1 provider 추상화
- `domain/ai` 포트: `AiChatClient`(또는 `LlmClient`) 인터페이스 — 도메인은 Spring/HTTP/provider 모름.
- `infra/ai/claude`: Claude 어댑터(`claude-sonnet-5`). *(구현 시 `claude-api` 스킬 참조)*
- 설정: `ai.provider`, `ai.claude.api-key`(env), 모델·타임아웃. provider 교체는 어댑터 추가 + 설정만으로.

### 5.2 멀티스텝 에이전트 (도구 활용)
- 도구(tool): 전시 상세 조회(`ExhibitionFacade.getDetail`), (선택) 유저 과거 기록 톤 참조.
- 엔드포인트:
  - `POST /api/v1/records/ai/questions` — 전시 맥락 기반 질문 3개 생성. "다른 질문 보기"=재요청(다양성 확보).
  - `POST /api/v1/records/ai/compose` — Q&A 답변 → **감상문 본문 생성**(동기). "다시 다듬기"=재호출.
- 요청/응답 스키마, 프롬프트, 에러/타임아웃/비용 가드, 재시도 정책 정의.
- 테스트: LLM 포트 모킹한 단위/통합(실연동은 별도 e2e).
- FE 흐름: compose 결과를 사용자가 수정·확정 → **P0의 create로 저장**(content=최종본, writeMode=AI).

## 6. 전시 수집(카탈로그) 동작 확인 + 더미 클라이언트

### 6.1 전시 수집 메커니즘(현재)
- `ExhibitionCatalogBootSync`(ApplicationRunner): **부팅 시** 공공데이터(문화포털, data.go.kr `B553457/cultureinfo`, realm `D000` 전시)에서 `syncCatalog()`.
- `ExhibitionSyncScheduler`: **매시 정각**(`0 0 * * * *`) 재동기화.
- 서비스키는 `application.yaml` 기본값 존재(env `CULTURE_API_KEY` 오버라이드). 키 무효/미설정이면 외부 호출 스킵 → 0건.
- 데모 시드(`EXHIBITION_DEMO_SEED`)는 기본 off.

### 6.2 더미 클라이언트 (검증용)
- 목적: 실행 서버(`localhost:18080`) 대상, **전시 수집 결과 + 직접 작성 기록 플로우**가 실제로 동작하는지 시각 확인.
- 형태: 정적 단일 페이지(HTML/JS) — `client-demo/` (또는 기존 `static/exhibitions.html` 패턴).
- 검증 시나리오:
  1. 게스트 로그인 → accessToken 확보
  2. 전시 목록 조회(수집된 실제 전시 노출 확인) → 하나 선택
  3. **직접 작성** 모드로 감상(≤300자)·감정 키워드·(선택)미디어 입력 → 저장
  4. 저장된 기록 상세/목록 확인
- 산출: 클라이언트 파일 + 확인 결과(전시 건수·기록 왕복) 리포트.

## 7. 단계별 체크리스트

- [x] 플랜 문서화(본 문서)
- [x] 전시 수집 실동작 확인 — 부팅 동기화 265건 적재, 매시 스케줄러 ~277건, 목록 API 253건(문화포털 실데이터)
- [x] 더미 클라이언트(`client-demo/`) 작성 + 직접 작성 플로우 브라우저 왕복 확인(게스트→전시목록→작성→저장 OK)
- [x] P0: DTO·서비스·검증(content 300·미디어 5·감정 라벨 ≤10)·AI필드 제거·테스트·Postman — 기록 단위/컨트롤러 테스트 통과
- [ ] P1: provider 추상화 + 에이전트(questions/compose) + 테스트

### 검증 로그(2026-07-05)
- 전시 수집: `ExhibitionCatalogBootSync` 부팅 시 265건, `ExhibitionSyncScheduler` 매시 재동기화 정상. `GET /api/v1/exhibitions` 253건(포스터 이미지 포함) 반환 확인.
- 직접 작성: 더미 클라이언트로 게스트 로그인 → 실제 전시 선택 → 감정 키워드·감상 입력 → `POST /api/v1/records`(DIRECT) 저장 → "기록이 저장되었어요"까지 브라우저 end-to-end 확인. 콘솔 에러 0.
- 비고: 현재 백엔드 계약(`emotionCodes`, AI 필드 optional) 기준으로 동작. P0 반영 시 `emotionKeywords`·본문 300자·미디어 5로 갱신 예정.

## 8. 오픈 이슈
- 미디어 업로드: 현재 create는 `url`+`sizeBytes`를 직접 받음(사전 업로드 가정). 실제 파일 업로드/스토리지(S3 등) 엔드포인트는 별도 과제.
- 감정 프리셋 표준 목록의 소유 주체(FE vs BE 공유 상수).
- 본문 300자 제한의 최종 확정(와이어프레임 기준값).
- 아카이브 카드(요약·대표감정) 정식 디자인 시 AI 필드 재도입 여부.
