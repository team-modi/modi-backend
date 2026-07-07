# auth — 인증

> 공통 응답/에러 규약은 [공통 규약](../공통/README.md) 참조. 상태: 전 API ✅ 기존 구현(베타 범위 변경 없음).
> 로그인 성공 시 access/refresh는 **쿠키로도 내려간다**(`setAuthCookies`). 응답 바디의 `accessToken`은 편의 병행 제공.

## API 목록

| # | 기능 | Method | Path | 인증 | 상태 |
|---|---|---|---|---|---|
| 3.1 | 소셜 로그인 | POST | `/api/v1/auth/login/{provider}` | ⚪ | ✅ |
| 3.2 | 게스트 로그인 | POST | `/api/v1/auth/guest` | ⚪ | ✅ |
| 3.3 | 토큰 재발급 | POST | `/api/v1/auth/refresh` | ⚪(refresh 쿠키) | ✅ |
| 3.4 | 로그아웃 | POST | `/api/v1/auth/logout` | 🔒 | ✅ |

---

## 3.1 소셜 로그인 `POST /api/v1/auth/login/{provider}` ✅

카카오/구글 인가 코드를 받아 회원을 찾거나 생성하고 JWT를 발급한다.
- Path: `provider` — `kakao` | `google`
- `redirectUri`는 서버 화이트리스트 검증 대상(FE 값 그대로 신뢰 안 함).

**요청 예시**
```http
POST /api/v1/auth/login/kakao HTTP/1.1
Host: api.modi.app
Content-Type: application/json

{
  "code": "GNPvL2z0k9...",
  "redirectUri": "https://app.modi.app/oauth/kakao"
}
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "userId": 12, "nickname": "야채곰창", "name": "김모디",
      "profileCompleted": false, "provider": "KAKAO",
      "email": "user@kakao.com", "ageGroup": "UNSPECIFIED", "birthYear": null
    }
  }
}
```
- `profileCompleted=false`면 온보딩(프로필 완성) 유도.

**에러 응답 예시** (허용 외 redirectUri)
```json
{
  "meta": { "result": "FAIL", "errorCode": "INVALID_REDIRECT_URI", "message": "허용되지 않은 redirectUri입니다." },
  "data": null
}
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `UNSUPPORTED_PROVIDER` | 400 | `{provider}`가 kakao/google 외 |
| `INVALID_REDIRECT_URI` | 400 | 화이트리스트에 없는 redirectUri |
| `INVALID_INPUT` | 400 | code/redirectUri 누락(공백) |
| `OAUTH_COMMUNICATION_FAILED` | 502 | 소셜 서버 통신 실패 |
| `SOCIAL_ACCOUNT_LINK_BROKEN` | 500 | 연결된 사용자 정보 유실 |

---

## 3.2 게스트 로그인 `POST /api/v1/auth/guest` ✅

비회원 체험용 토큰 발급. 기록·프로필 등 로그인 전용 기능을 게스트도 사용 가능.

**요청 예시**
```http
POST /api/v1/auth/guest HTTP/1.1
Host: api.modi.app
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "userId": 907, "nickname": "게스트-1a2b", "name": null,
      "profileCompleted": false, "provider": "GUEST",
      "email": null, "ageGroup": "UNSPECIFIED", "birthYear": null
    }
  }
}
```

**에러 응답 예시** (서버 오류)
```json
{ "meta": { "result": "FAIL", "errorCode": "INTERNAL_ERROR", "message": "서버 오류가 발생했습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INTERNAL_ERROR` | 500 | 게스트 계정 생성/토큰 발급 실패 등 서버 오류 |

---

## 3.3 토큰 재발급 `POST /api/v1/auth/refresh` ✅

refresh 토큰(쿠키)으로 access 토큰 재발급.

**요청 예시**
```http
POST /api/v1/auth/refresh HTTP/1.1
Host: api.modi.app
Cookie: refreshToken=eyJhbGciOiJIUzI1NiJ9...
```

**성공 응답 (200)** — 새 `TokenResponse`(access 갱신, 쿠키 재설정). 3.1과 동일 스키마.
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...(새 토큰)",
    "user": { "userId": 12, "nickname": "야채곰창", "name": "김모디", "profileCompleted": true, "provider": "KAKAO", "email": "user@kakao.com", "ageGroup": "TWENTIES", "birthYear": null }
  }
}
```

**에러 응답 예시** (만료된 refresh)
```json
{
  "meta": { "result": "FAIL", "errorCode": "INVALID_REFRESH_TOKEN", "message": "재발급 토큰 검증에 실패했습니다." },
  "data": null
}
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `NO_REFRESH_TOKEN` | 401 | refresh 쿠키 없음 |
| `INVALID_REFRESH_TOKEN` | 401 | refresh 만료·위변조·검증 실패 |

---

## 3.4 로그아웃 `POST /api/v1/auth/logout` 🔒

현재 세션 무효화(refresh 폐기·쿠키 삭제).

**요청 예시**
```http
POST /api/v1/auth/logout HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)**
```json
{ "meta": { "result": "SUCCESS", "errorCode": null, "message": null }, "data": null }
```

**에러 응답 예시** (토큰 만료)
```json
{
  "meta": { "result": "FAIL", "errorCode": "INVALID_ACCESS_TOKEN", "message": "유효하지 않은 인증 토큰입니다." },
  "data": null
}
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `NO_ACCESS_TOKEN` | 401 | access 토큰 없음 |
| `INVALID_ACCESS_TOKEN` | 401 | access 만료·위변조 |
