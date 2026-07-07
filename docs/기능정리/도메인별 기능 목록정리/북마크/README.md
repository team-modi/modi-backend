# bookmark — 관심 전시 🆕

> 공통 규약: [공통 규약](../공통/README.md). 화면: [02] 탐색 🔖 · [03] 상세 🔖 · [06] 프로필 관심 전시.
> ⚠️ 기존 구현의 북마크는 **"기록(record) 북마크"** 뿐이다. 와이어프레임의 관심(🔖)은 **"전시(exhibition) 북마크"** — 신규 도메인. `GET /users/me`의 `stats.bookmarkCount`도 전시 북마크 수로 의미 변경.

## API 목록

| # | 기능 | Method | Path | 인증 | 상태 |
|---|---|---|---|---|---|
| 6.1 | 관심 전시 등록 | POST | `/api/v1/exhibitions/{exhibitionId}/bookmark` | 🔒 | 🆕 |
| 6.2 | 관심 전시 해제 | DELETE | `/api/v1/exhibitions/{exhibitionId}/bookmark` | 🔒 | 🆕 |
| 6.3 | 관심 전시 목록 | GET | `/api/v1/users/me/bookmarks` | 🔒 | 🆕 |

---

## 6.1 관심 전시 등록 `POST /api/v1/exhibitions/{exhibitionId}/bookmark` 🆕

탐색 카드·상세의 🔖 토글. **멱등** — 이미 등록돼 있어도 성공.

**요청 예시**
```http
POST /api/v1/exhibitions/51/bookmark HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "exhibitionId": 51, "bookmarked": true }
}
```

**에러 응답 예시** (없는 전시)
```json
{ "meta": { "result": "FAIL", "errorCode": "NOT_FOUND", "message": "요청한 전시를 찾을 수 없습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `NOT_FOUND` | 404 | 없는 전시(`EXHIBITION_NOT_FOUND`) |
| `UNAUTHORIZED` | 401 | 미인증 |

---

## 6.2 관심 전시 해제 `DELETE /api/v1/exhibitions/{exhibitionId}/bookmark` 🆕

**멱등** — 이미 해제 상태여도 성공.

**요청 예시**
```http
DELETE /api/v1/exhibitions/51/bookmark HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "exhibitionId": 51, "bookmarked": false }
}
```

**에러 응답 예시** (없는 전시)
```json
{ "meta": { "result": "FAIL", "errorCode": "NOT_FOUND", "message": "요청한 전시를 찾을 수 없습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `NOT_FOUND` | 404 | 없는 전시(`EXHIBITION_NOT_FOUND`) |
| `UNAUTHORIZED` | 401 | 미인증 |

---

## 6.3 관심 전시 목록 `GET /api/v1/users/me/bookmarks` 🔒 🆕

프로필 > 관심 전시.

**요청 Query**

| 파라미터 | 값 |
|---|---|
| `sort` | `latest`(등록 최신순, 기본) \| `ending`(종료 임박순) |
| `cursor`,`size` | 커서 페이지네이션 |

**요청 예시**
```http
GET /api/v1/users/me/bookmarks?sort=ending&size=20 HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)** — `CursorResponse<ExhibitionListItem>` ([전시](../전시/README.md) 목록 항목과 동일 스키마, `bookmarked`는 항상 true).
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "exhibitionId": 51, "type": "CATALOG", "title": "희박한 공기 Thin Air",
        "posterUrl": "https://cdn.modi.app/exhibitions/51/poster.jpg",
        "startDate": "2026-07-01", "endDate": "2026-08-15", "place": "동작아트갤러리/서울",
        "region": "SEOUL", "category": "PAINTING", "artistSummary": "김미경 외 10인",
        "dDay": 5, "free": true, "bookmarked": true
      }
    ],
    "nextCursor": null, "hasNext": false, "totalCount": 10
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
