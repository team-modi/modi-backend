# notification — 알림 🆕

> 공통 규약: [공통 규약](../공통/README.md). 화면: [01] 홈 우측 상단 종 아이콘.

## API 목록

| # | 기능 | Method | Path | 인증 | 상태 |
|---|---|---|---|---|---|
| 9.1 | 알림 목록 조회 | GET | `/api/v1/notifications` | 🔒 | 🆕 |
| 9.2 | 알림 읽음 처리 | PUT | `/api/v1/notifications/{notificationId}/read` | 🔒 | 🆕 |

---

## 9.1 알림 목록 조회 `GET /api/v1/notifications` 🆕

**요청 Query** — `cursor`,`size`(커서 페이지네이션).

**요청 예시**
```http
GET /api/v1/notifications?size=20 HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)** — `CursorResponse<NotificationItem>`
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "notificationId": 44,
        "type": "REMIND",
        "title": "오늘의 여운이 도착했어요",
        "body": "1주일 전 기록한 '조용한 호숫가', 지금 다시 보면 어떤가요?",
        "targetId": 5,
        "read": false,
        "createdAt": "2026-07-10T09:00:00"
      }
    ],
    "nextCursor": null, "hasNext": false, "totalCount": 3
  }
}
```
- `type`: `REMIND`(targetId=remindId) | `NOTICE`(targetId=null).

**에러 응답 예시** (커서-조건 불일치)
```json
{ "meta": { "result": "FAIL", "errorCode": "INVALID_CURSOR", "message": "입력값이 올바르지 않습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_CURSOR` | 400 | 손상된 커서 |
| `UNAUTHORIZED` | 401 | 미인증 |

---

## 9.2 알림 읽음 처리 `PUT /api/v1/notifications/{notificationId}/read` 🆕

**요청 예시**
```http
PUT /api/v1/notifications/44/read HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "notificationId": 44, "read": true }
}
```

**에러 응답 예시** (타인/없는 알림)
```json
{ "meta": { "result": "FAIL", "errorCode": "NOTIFICATION_NOT_FOUND", "message": "알림을 찾을 수 없습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `NOTIFICATION_NOT_FOUND` 🆕 | 404 | 없는/타인 알림 |
| `UNAUTHORIZED` | 401 | 미인증 |
