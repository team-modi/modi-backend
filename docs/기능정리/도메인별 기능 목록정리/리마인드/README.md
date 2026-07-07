# remind — 리마인드 / 오늘의 여운 🆕

> 공통 규약: [공통 규약](../공통/README.md). 화면: [07] 리마인드.
> 리마인드 생성은 API가 아니라 **스케줄러**(기록 저장 +7일) + 알림 발송. 감정 변화 요약은 **AI 생성 문장**(확정).

## API 목록

| # | 기능 | Method | Path | 인증 | 상태 |
|---|---|---|---|---|---|
| 8.1 | 오늘의 리마인드 조회 | GET | `/api/v1/reminds/today` | 🔒 | 🆕 |
| 8.2 | 오늘의 여운 저장 | POST | `/api/v1/reminds/{remindId}/afterglow` | 🔒 | 🆕 |

---

## 8.1 오늘의 리마인드 조회 `GET /api/v1/reminds/today` 🆕

"1주일 전, 이 전시를 기록했어요" 3장 화면을 한 응답으로 렌더.

**요청 예시**
```http
GET /api/v1/reminds/today HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200) — 리마인드 있음**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "remindId": 5, "recordId": 31, "daysAgo": 7,
    "exhibition": { "exhibitionId": 51, "title": "조용한 호숫가", "artistSummary": "김선명", "posterUrl": "…", "place": "동작아트갤러리/서울" },
    "viewedAt": "2026-07-03",
    "sceneMediaUrl": "https://cdn.modi.app/records/31/1.jpg",
    "contentSnapshot": "빛이 천천히 번지는 전시실을 지나며…",
    "emotionCodes": ["평화로운", "차분한", "고요한"],
    "answered": false
  }
}
```

**성공 응답 (200) — 리마인드 없음** (에러 아님)
```json
{ "meta": { "result": "SUCCESS", "errorCode": null, "message": null }, "data": null }
```

**에러 응답 예시** (미인증)
```json
{ "meta": { "result": "FAIL", "errorCode": "UNAUTHORIZED", "message": "인증이 필요합니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `UNAUTHORIZED` | 401 | 미인증 |

---

## 8.2 오늘의 여운 저장 `POST /api/v1/reminds/{remindId}/afterglow` 🆕

"지금 다시 보니 어떤가요?" → 감정 재선택 + 한 줄. 응답으로 감정 변화 요약(AI) 반환.

**요청 예시**
```http
POST /api/v1/reminds/5/afterglow HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "emotionCodes": ["슬픈", "생생한"],
  "sentence": "당시에는 강렬한 세계가 생생했는데, 다시 보니 반전되는 슬픈 분위기가 더 다가온다"
}
```
- `emotionCodes` 항목당 ≤10자, `sentence` ≤300자.

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "afterglowId": 2, "recordId": 31,
    "beforeEmotions": ["평화로운", "차분한", "고요한"],
    "afterEmotions": ["슬픈", "생생한"],
    "emotionChangeSummary": "고요했던 첫 감상이 시간이 지나며 아련한 슬픔으로 번졌어요"
  }
}
```
- `emotionChangeSummary`는 before/after 감정 + 감상을 입력으로 LLM 호출(compose처럼 비동기 처리 가능).

**에러 응답 예시** (이미 여운 저장함)
```json
{ "meta": { "result": "FAIL", "errorCode": "REMIND_ALREADY_ANSWERED", "message": "이미 오늘의 여운을 남겼습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_INPUT` | 400 | emotionCodes 빈 값/10자 초과, sentence 300자 초과 |
| `REMIND_NOT_FOUND` 🆕 | 404 | 없는/타인 리마인드 |
| `REMIND_ALREADY_ANSWERED` 🆕 | 409 | 여운 중복 저장 |
| `AI_GENERATION_FAILED` | 502 | 요약 생성 실패(저장은 성공시키고 요약만 재시도할지 정책 확정 필요) |
