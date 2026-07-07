# record — 전시 관람 기록 / 아카이브

> 공통 규약: [공통 규약](../공통/README.md). 화면: [04] 기록 · [05] 아카이브.
> 감정 키워드는 프리셋/커스텀 구분 없이 **한글 라벨(≤10자) 그대로 저장**(`emotionCodes` 통합). 미디어 `type`은 `PHOTO`|`VIDEO`, `sizeBytes` 필수(PHOTO ≤10MB·VIDEO ≤100MB, 최대 5개).

## API 목록

| # | 기능 | Method | Path | 인증 | 상태 |
|---|---|---|---|---|---|
| 7.1 | 감정 키워드 마스터 | GET | `/api/v1/emotion-keywords` | ⚪ | 🆕 |
| 7.2 | 기록 생성 | POST | `/api/v1/records` | 🔒 | 🔧 |
| 7.3 | 아카이브 목록 | GET | `/api/v1/records` | 🔒 | 🔧 |
| 7.4 | 아카이브 상세 | GET | `/api/v1/records/{recordId}` | 🔒 | 🔧 |
| 7.5 | 기록 수정 | PUT | `/api/v1/records/{recordId}` | 🔒 | ✅ |
| 7.6 | 기록 삭제 | DELETE | `/api/v1/records/{recordId}` | 🔒 | ✅ |
| 7.7 | 기록한 전시 목록 | GET | `/api/v1/records/exhibitions/visited` | 🔒 | 🔧 |
| 7.8 | AI 질문 생성 | POST | `/api/v1/records/ai/questions` | 🔒 | ✅ |
| 7.9 | AI 감상문 초안 | POST | `/api/v1/records/ai/compose` | 🔒 | ✅ |
| 7.10 | 기록 북마크 | POST·DELETE | `/api/v1/records/{recordId}/bookmark` | 🔒 | ✅(베타 UI 미노출) |

---

## 7.1 감정 키워드 마스터 `GET /api/v1/emotion-keywords` 🆕

**요청 예시**
```http
GET /api/v1/emotion-keywords HTTP/1.1
Host: api.modi.app
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "keywords": ["슬픈", "강렬한", "재미있는", "유쾌한", "서정적인", "화나는", "아름다운", "관심있는"] }
}
```
- 2026-07-07 목업 이미지 기준(필러 `특별전`·`콩` 제외). 최종 라벨 세트 확정 시 배열만 교체.

**에러 응답 예시** (서버 오류)
```json
{ "meta": { "result": "FAIL", "errorCode": "INTERNAL_ERROR", "message": "서버 오류가 발생했습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INTERNAL_ERROR` | 500 | 키워드 조회 중 서버 오류 |

---

## 7.2 기록 생성 `POST /api/v1/records` 🔧

직접 작성·AI 작성 모두 최종 저장은 이 API. 🔧 현재 201 반환 → 컨벤션(전부 200)으로 정리 필요.

**요청 예시**
```http
POST /api/v1/records HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "exhibitionId": 51,
  "writeMode": "DIRECT",
  "viewedAt": "2026-07-01",
  "content": "마음에 잔잔한 호숫가에 돌이 하나 떨어지는 느낌이었다.",
  "emotionCodes": ["차분한", "고요한", "물비린내"],
  "media": [
    { "type": "PHOTO", "url": "https://cdn.modi.app/records/tmp/1.jpg", "sortOrder": 0, "sizeBytes": 2048000 },
    { "type": "VIDEO", "url": "https://cdn.modi.app/records/tmp/2.mp4", "sortOrder": 1, "sizeBytes": 52428800 }
  ]
}
```
- `writeMode`: `DIRECT`(직접) | `AI`(질문으로 작성 — compose 결과 확정본). `content`≤300자, `emotionCodes` 항목당 ≤10자.

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "recordId": 31, "exhibitionId": 51, "writeMode": "DIRECT",
    "viewedAt": "2026-07-01", "aiStatus": "READY",
    "createdAt": "2026-07-01T14:20:00+09:00"
  }
}
```
- `aiStatus`: `READY`|`PENDING`|`FAILED`(현재 항상 READY — AI 후처리 없음).

**에러 응답 예시** (미디어 용량 초과)
```json
{ "meta": { "result": "FAIL", "errorCode": "INVALID_MEDIA", "message": "첨부할 수 없는 미디어입니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_INPUT` | 400 | content 공백/300자 초과, emotionCodes 비었거나 10자 초과, viewedAt 미래(`INVALID_RECORD_INPUT`) |
| `INVALID_MEDIA` | 400 | 미디어 5개 초과, 용량 초과, 미지원 type(`INVALID_RECORD_MEDIA`) |
| `NOT_FOUND` | 404 | 없는 exhibitionId(`EXHIBITION_NOT_FOUND`) |
| `UNAUTHORIZED` | 401 | 미인증 |

---

## 7.3 아카이브 목록 `GET /api/v1/records` 🔧

**요청 Query**

| 파라미터 | 값 |
|---|---|
| `sort` | `latest`(기본) \| `oldest` — 🔧 명시 계약으로 확정 |
| `keyword`,`emotion`,`exhibitionId`,`bookmarked`,`writeMode`,`fromViewedAt`,`toViewedAt` | 기존 필터(베타 UI는 정렬만 사용) |
| `cursor`,`size` | 커서 페이지네이션 |

**요청 예시**
```http
GET /api/v1/records?sort=latest&size=20 HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)** — `CursorResponse<RecordListItem>`
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "recordId": 31, "exhibitionId": 51, "thumbnailUrl": "https://cdn.modi.app/records/31/thumb.jpg",
        "aiSummary": null, "representativeEmotion": null, "bookmarked": false, "viewedAt": "2026-07-01",
        "emotionCodes": ["평화로운", "차분한", "고요한"],
        "exhibitionTitle": "조용한 호숫가", "exhibitionType": "CUSTOM", "exhibitionPosterUrl": "…",
        "exhibitionPlace": "아리랑 문화관", "exhibitionRegion": "SEOUL", "exhibitionCategory": "PHOTO",
        "exhibitionStartDate": "2026-06-24", "exhibitionEndDate": "2026-07-31"
      }
    ],
    "nextCursor": "eyJzb3J0IjoibGF0ZXN0IiwibGFzdElkIjozMX0",
    "hasNext": true, "totalCount": 6
  }
}
```
- `emotionCodes`(상위 3개)는 🆕 그리드 칩용.

**에러 응답 예시** (커서-조건 불일치)
```json
{ "meta": { "result": "FAIL", "errorCode": "INVALID_CURSOR", "message": "입력값이 올바르지 않습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_CURSOR` | 400 | 정렬 변경 후 이전 커서 재사용 등 커서-조건 불일치 |
| `UNAUTHORIZED` | 401 | 미인증 |

---

## 7.4 아카이브 상세 `GET /api/v1/records/{recordId}` 🔧

본인 기록만. 🔧 `writeMode`·`afterglows[]` 추가.

**요청 예시**
```http
GET /api/v1/records/31 HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "recordId": 31, "exhibitionId": 51, "viewedAt": "2026-07-03",
    "content": "마음에 잔잔한 호숫가에 돌이 하나 떨어지는 느낌이었다.",
    "writeMode": "DIRECT",
    "aiSummary": null, "aiKeywords": [], "userKeywords": [],
    "emotionCodes": ["평화로운", "차분한", "고요한"],
    "representativeEmotion": null, "cardPhrase": null, "bookmarked": false,
    "media": [ { "type": "PHOTO", "url": "https://cdn.modi.app/records/31/1.jpg", "sortOrder": 0, "sizeBytes": 2048000 } ],
    "exhibitionTitle": "조용한 호숫가", "exhibitionPosterUrl": "…", "exhibitionPlace": "아리랑 문화관",
    "exhibitionStartDate": "2026-06-24", "exhibitionEndDate": "2026-07-31",
    "afterglows": [
      {
        "afterglowId": 2, "recordedAt": "2026-07-10",
        "emotionCodes": ["그리운", "따뜻한"],
        "sentence": "지금 다시 보니 반전되는 슬픈 분위기가 더 생생하게 다가온다",
        "emotionChangeSummary": "평화로움 → 그리움으로 여운이 깊어졌어요"
      }
    ]
  }
}
```

**에러 응답 예시** (타인 기록 접근)
```json
{ "meta": { "result": "FAIL", "errorCode": "FORBIDDEN", "message": "기록에 접근할 권한이 없습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `NOT_FOUND` | 404 | 없는 기록(`RECORD_NOT_FOUND`) |
| `FORBIDDEN` | 403 | 타인 기록 접근(`FORBIDDEN_RECORD`) |

---

## 7.5 기록 수정 `PUT /api/v1/records/{recordId}` ✅

감상·감정·미디어 교체(AI 산출 필드는 클라이언트 미전송).

**요청 예시**
```http
PUT /api/v1/records/31 HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "viewedAt": "2026-07-03",
  "content": "다시 보니 더 잔잔했다.",
  "emotionCodes": ["차분한", "고요한"],
  "media": [ { "type": "PHOTO", "url": "https://cdn.modi.app/records/31/1.jpg", "sortOrder": 0, "sizeBytes": 2048000 } ]
}
```

**성공 응답 (200)** — 상세(7.4)와 동일한 스키마의 갱신본.
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "recordId": 31, "exhibitionId": 51, "viewedAt": "2026-07-03",
    "content": "다시 보니 더 잔잔했다.",
    "writeMode": "DIRECT",
    "aiSummary": null, "aiKeywords": [], "userKeywords": [],
    "emotionCodes": ["차분한", "고요한"],
    "representativeEmotion": null, "cardPhrase": null, "bookmarked": false,
    "media": [ { "type": "PHOTO", "url": "https://cdn.modi.app/records/31/1.jpg", "sortOrder": 0, "sizeBytes": 2048000 } ],
    "exhibitionTitle": "조용한 호숫가", "exhibitionPosterUrl": "…", "exhibitionPlace": "아리랑 문화관",
    "exhibitionStartDate": "2026-06-24", "exhibitionEndDate": "2026-07-31",
    "afterglows": []
  }
}
```

**에러 응답 예시** (감상문 300자 초과)
```json
{ "meta": { "result": "FAIL", "errorCode": "INVALID_INPUT", "message": "기록 입력값이 올바르지 않습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_INPUT` | 400 | content 공백/300자 초과, emotionCodes 비었거나 10자 초과 |
| `INVALID_MEDIA` | 400 | 미디어 5개 초과·용량 초과·미지원 type |
| `NOT_FOUND` | 404 | 없는 기록(`RECORD_NOT_FOUND`) |
| `FORBIDDEN` | 403 | 타인 기록 수정(`FORBIDDEN_RECORD`) |

---

## 7.6 기록 삭제 `DELETE /api/v1/records/{recordId}` ✅

soft-delete.

**요청 예시**
```http
DELETE /api/v1/records/31 HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)**
```json
{ "meta": { "result": "SUCCESS", "errorCode": null, "message": null }, "data": null }
```

**에러 응답 예시** (타인 기록 삭제)
```json
{ "meta": { "result": "FAIL", "errorCode": "FORBIDDEN", "message": "기록에 접근할 권한이 없습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `NOT_FOUND` | 404 | 없는 기록(`RECORD_NOT_FOUND`) |
| `FORBIDDEN` | 403 | 타인 기록 삭제(`FORBIDDEN_RECORD`) |

---

## 7.7 기록한 전시 목록 `GET /api/v1/records/exhibitions/visited` 🔧

프로필 > 다녀온 전시. 목록 조회와 동일 파라미터(`sort=latest|oldest`, 커서).

**요청 예시**
```http
GET /api/v1/records/exhibitions/visited?sort=latest&size=20 HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)** — `CursorResponse<RecordListItem>` (아카이브 목록 7.3과 동일한 항목 스키마, 전시 카드 중심으로 사용).
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "recordId": 31, "exhibitionId": 51, "thumbnailUrl": "https://cdn.modi.app/records/31/thumb.jpg",
        "aiSummary": null, "representativeEmotion": null, "bookmarked": false, "viewedAt": "2026-07-01",
        "emotionCodes": ["평화로운", "차분한", "고요한"],
        "exhibitionTitle": "조용한 호숫가", "exhibitionType": "CUSTOM", "exhibitionPosterUrl": "…",
        "exhibitionPlace": "아리랑 문화관", "exhibitionRegion": "SEOUL", "exhibitionCategory": "PHOTO",
        "exhibitionStartDate": "2026-06-24", "exhibitionEndDate": "2026-07-31"
      }
    ],
    "nextCursor": "eyJzb3J0IjoibGF0ZXN0IiwibGFzdElkIjozMX0",
    "hasNext": true, "totalCount": 6
  }
}
```

**에러 응답 예시** (커서-조건 불일치)
```json
{ "meta": { "result": "FAIL", "errorCode": "INVALID_CURSOR", "message": "입력값이 올바르지 않습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_CURSOR` | 400 | 정렬 변경 후 이전 커서 재사용 등 커서-조건 불일치 |
| `UNAUTHORIZED` | 401 | 미인증 |

> ※ 같은 전시 2회 기록 시 중복 노출 정책은 미확정(상위 폴더 `../../04_기존 구현 vs 와이어프레임 차이 체크리스트.md` C-4).

---

## 7.8 AI 질문 생성 `POST /api/v1/records/ai/questions` ✅

비동기(`CompletableFuture`).

**요청 예시**
```http
POST /api/v1/records/ai/questions HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
Content-Type: application/json

{ "exhibitionId": 51 }
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "questions": ["오늘 가장 오래 시선이 머문 장면은?", "그 장면에서 어떤 감정을 느꼈나요?", "이 전시를 한 문장으로 남긴다면?"] }
}
```

**에러 응답 예시** (AI 미설정)
```json
{ "meta": { "result": "FAIL", "errorCode": "AI_DISABLED", "message": "AI 기능이 설정되지 않았습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `NOT_FOUND` | 404 | 없는 전시 |
| `AI_DISABLED` | 503 | AI 기능 미설정 |
| `AI_GENERATION_FAILED` | 502 | 모델 응답 실패 |
| `AI_RATE_LIMITED` | 429 | 호출 제한 |

---

## 7.9 AI 감상문 초안 `POST /api/v1/records/ai/compose` ✅

**요청 예시**
```http
POST /api/v1/records/ai/compose HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "exhibitionId": 51,
  "answers": [
    { "question": "오늘 가장 오래 시선이 머문 장면은?", "answer": "빛이 천천히 번지는 전시실 복도" },
    { "question": "그 장면에서 어떤 감정을 느꼈나요?", "answer": "낯설고 궁금했다" },
    { "question": "이 전시를 한 문장으로 남긴다면?", "answer": "기억이 색으로 남는 전시" }
  ]
}
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "content": "빛이 천천히 번지는 전시실을 지나며, 어릴 적 외할머니 집 마당이 떠올랐다. …" }
}
```
- 이 `content`를 AI 정리 화면에 표시(사용자 수정 가능) → 최종 저장은 7.2(`writeMode: "AI"`).

**에러 응답 예시** (모델 응답 실패)
```json
{ "meta": { "result": "FAIL", "errorCode": "AI_GENERATION_FAILED", "message": "AI 응답 생성에 실패했습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_INPUT` | 400 | answers 비었거나 형식 오류 |
| `NOT_FOUND` | 404 | 없는 전시 |
| `AI_DISABLED` | 503 | AI 기능 미설정 |
| `AI_GENERATION_FAILED` | 502 | 모델 응답 실패 |
| `AI_RATE_LIMITED` | 429 | 호출 제한 |

---

## 7.10 기록 북마크 `POST·DELETE /api/v1/records/{recordId}/bookmark` ✅

기존 구현 유지(베타 UI 미노출). 멱등.

**요청 예시**
```http
POST /api/v1/records/31/bookmark HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "recordId": 31, "bookmarked": true }
}
```
- DELETE 시 `"bookmarked": false`.

**에러 응답 예시** (타인 기록)
```json
{ "meta": { "result": "FAIL", "errorCode": "FORBIDDEN", "message": "기록에 접근할 권한이 없습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `NOT_FOUND` | 404 | 없는 기록(`RECORD_NOT_FOUND`) |
| `FORBIDDEN` | 403 | 타인 기록 북마크(`FORBIDDEN_RECORD`) |
