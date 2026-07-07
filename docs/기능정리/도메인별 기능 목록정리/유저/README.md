# user — 회원/프로필

> 공통 규약: [공통 규약](../공통/README.md). 화면: [06] 프로필.
> ⚠️ `ageGroup` 실제 enum은 `TEENS`·`TWENTIES`·`THIRTIES`·`FORTIES`·`FIFTIES_PLUS`·`UNSPECIFIED`(10년 단위). 와이어프레임 프로필 수정은 "20~24세" 5년 단위로 보임 → 정밀도 불일치, 기획 확정 시 enum 재정의 필요.

## API 목록

| # | 기능 | Method | Path | 인증 | 상태 |
|---|---|---|---|---|---|
| 4.1 | 내 프로필 조회 | GET | `/api/v1/users/me` | 🔒 | ✅ |
| 4.2 | 프로필 수정 | PUT | `/api/v1/users/me/profile` | 🔒 | ✅ |
| 4.3 | 알림 설정 조회 | GET | `/api/v1/users/me/notification-settings` | 🔒 | 🆕 |
| 4.4 | 알림 설정 수정 | PUT | `/api/v1/users/me/notification-settings` | 🔒 | 🆕 |
| 4.5 | 회원 탈퇴 | DELETE | `/api/v1/users/me` | 🔒 | 🆕 |

> 문의하기·이용약관은 외부 링크(정적/폼) — 서버 API 없음.

---

## 4.1 내 프로필 조회 `GET /api/v1/users/me` ✅

프로필 화면 상단(닉네임·보관함 수치·감정 키워드).

**요청 예시**
```http
GET /api/v1/users/me HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "userId": 12, "provider": "KAKAO", "nickname": "야채곰창",
    "profileImageUrl": "https://cdn.modi.app/users/12/profile.jpg",
    "ageGroup": "TWENTIES", "birthYear": null,
    "residenceRegion": "SEOUL", "residenceDistrict": "마포구",
    "tasteKeywords": ["감성적인", "따뜻한", "아름다운", "편안한"],
    "stats": { "recordCount": 6, "exhibitionCount": 6, "bookmarkCount": 10 }
  }
}
```
- "다녀온 전시 6" = `stats.exhibitionCount`, "관심 전시 10" = `stats.bookmarkCount`(🔧 전시 북마크 수로 의미 변경 — [관심 전시](../북마크/README.md) 참조), "나의 감정 키워드" = `tasteKeywords`.

**에러 응답 예시** (미인증)
```json
{ "meta": { "result": "FAIL", "errorCode": "UNAUTHORIZED", "message": "인증이 필요합니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `UNAUTHORIZED` | 401 | 미인증 |
| `USER_NOT_FOUND` | 404 | 탈퇴/삭제된 사용자 |

---

## 4.2 프로필 수정 `PUT /api/v1/users/me/profile` ✅

모든 필드 선택(부분 수정). 프로필 이미지는 먼저 [파일 업로드](../공통/파일%20업로드%20support.md)로 URL 확보 후 전달.

**요청 예시**
```http
PUT /api/v1/users/me/profile HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "nickname": "야채곰창",
  "profileImageUrl": "https://cdn.modi.app/users/12/profile.jpg",
  "ageGroup": "TWENTIES",
  "residenceRegion": "SEOUL",
  "residenceDistrict": "마포구"
}
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "userId": 12, "provider": "KAKAO", "nickname": "야채곰창", "profileCompleted": true,
    "profileImageUrl": "https://cdn.modi.app/users/12/profile.jpg",
    "ageGroup": "TWENTIES", "birthYear": null,
    "residenceRegion": "SEOUL", "residenceDistrict": "마포구"
  }
}
```

**에러 응답 예시** (닉네임 규칙 위반)
```json
{ "meta": { "result": "FAIL", "errorCode": "INVALID_NICKNAME", "message": "닉네임은 1~20자, 공백만으로 구성할 수 없습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_NICKNAME` | 400 | 닉네임 1~20자 아님/공백만 |
| `INVALID_INPUT` | 400 | ageGroup 등 미정의 코드 |
| `USER_NOT_FOUND` | 404 | 사용자 없음 |

---

## 4.3 알림 설정 조회 `GET /api/v1/users/me/notification-settings` 🆕

**요청 예시**
```http
GET /api/v1/users/me/notification-settings HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "remindEnabled": true, "noticeEnabled": false }
}
```

**에러 응답 예시** (미인증)
```json
{ "meta": { "result": "FAIL", "errorCode": "UNAUTHORIZED", "message": "인증이 필요합니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `UNAUTHORIZED` | 401 | 미인증 |
| `USER_NOT_FOUND` | 404 | 탈퇴/삭제된 사용자 |

---

## 4.4 알림 설정 수정 `PUT /api/v1/users/me/notification-settings` 🆕

**요청 예시**
```http
PUT /api/v1/users/me/notification-settings HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
Content-Type: application/json

{ "remindEnabled": true, "noticeEnabled": false }
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "remindEnabled": true, "noticeEnabled": false }
}
```

**에러 응답 예시** (형식 오류)
```json
{ "meta": { "result": "FAIL", "errorCode": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_INPUT` | 400 | remindEnabled/noticeEnabled 형식 오류 |
| `UNAUTHORIZED` | 401 | 미인증 |

---

## 4.5 회원 탈퇴 `DELETE /api/v1/users/me` 🆕

soft-delete + 토큰 무효화.

**요청 예시**
```http
DELETE /api/v1/users/me HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)**
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
| `USER_NOT_FOUND` | 404 | 이미 탈퇴/삭제된 사용자 |
