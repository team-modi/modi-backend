# 공통 규약

> 모든 도메인 API가 공유하는 응답 포맷·페이지네이션·인증·공통 에러. 각 도메인 문서는 이 규약을 전제로 하며, 도메인 고유 에러만 개별 명세한다.

## 1. 응답 봉투 `ApiResponse`

성공·실패 모두 **HTTP 200**으로 내려가며, `meta.result`로 구분한다. (프로젝트 컨벤션: 성공 전부 200, 201·PATCH 미사용)

```json
// 성공
{ "meta": { "result": "SUCCESS", "errorCode": null, "message": null }, "data": { /* 페이로드 */ } }

// 실패
{ "meta": { "result": "FAIL", "errorCode": "NOT_FOUND", "message": "요청한 전시를 찾을 수 없습니다." }, "data": null }

// 검증 실패(필드 단위) — data에 fieldErrors 배열
{
  "meta": { "result": "FAIL", "errorCode": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다." },
  "data": [ { "field": "title", "value": "", "reason": "공백일 수 없습니다" } ]
}
```

- **주의**: 실패 시에도 HTTP 상태는 200이 아니다. 도메인 에러는 `ErrorCode.getStatus()`가 매핑한 상태코드(404·403·400 등)로 내려간다. 성공만 항상 200.
- 각 도메인 문서의 "에러 응답" 표에 적힌 `errorCode`는 응답 `meta.errorCode` 문자열 값이다(enum 이름이 아니라 `code()` 값 — 예: `EXHIBITION_NOT_FOUND` enum의 응답 코드는 `"NOT_FOUND"`).

## 2. 커서 페이지네이션 `CursorResponse` (목록 기본)

목록 조회는 **커서 방식이 기본**이다. (오프셋 `page`에서 전환 — 무한 스크롤 페이지 밀림 방지)

**요청**
```
GET /api/v1/exhibitions?size=20                 // 첫 페이지: cursor 생략
GET /api/v1/exhibitions?size=20&cursor=eyJ...    // 다음 페이지: 이전 응답의 nextCursor
```

**응답**
```json
{
  "content": [ /* 항목들 */ ],
  "nextCursor": "eyJzb3J0IjoibGF0ZXN0IiwibGFzdElkIjo1MX0",  // 마지막 페이지면 null
  "hasNext": true,
  "totalCount": 56    // 조건 기준 전체 건수("총 N개" UI용)
}
```

- 커서는 서버 발급 **opaque 문자열**(정렬 키 + 마지막 id 인코딩). 클라이언트는 해석하지 않고 그대로 재전달.
- **정렬·필터·검색 조건이 바뀌면 커서를 버리고 처음부터 재조회**한다. 조건과 커서 불일치 시 `INVALID_CURSOR`(400).
- `size` 기본 20, 최대 50.
- 적용: 전시 목록/섹션 전체보기/탐색, 관심 전시, 아카이브, 기록한 전시, 알림. (홈 배너·전시관 자동완성은 고정 개수라 커서 미적용)

## 3. 인증

- 헤더 `Authorization: Bearer {accessToken}` 또는 access 쿠키. `@Authentication LoginUser`가 검증·주입.
- 표기: 🔒 필수 · 🔓 선택(로그인 시 개인화 필드 채움) · ⚪ 불필요.
- 인증 실패는 공통 에러 `UNAUTHORIZED`(401) 또는 auth 도메인 토큰 에러(→ [인증 auth](../인증/README.md)).

## 4. 공통 에러 (ErrorType — 모든 도메인 공통)

| errorCode | HTTP | 의미 |
|---|---|---|
| `INVALID_INPUT` | 400 | 형식 검증 실패(Bean Validation·파싱 실패·잘못된 파라미터) |
| `INVALID_MEDIA` | 400 | 첨부 불가 미디어(형식·용량) |
| `INVALID_CURSOR` 🆕 | 400 | 커서-조건 불일치·손상된 커서 |
| `UNAUTHORIZED` | 401 | 인증 필요/실패 |
| `FORBIDDEN` | 403 | 접근 권한 없음 |
| `NOT_FOUND` | 404 | 리소스 없음 |
| `METHOD_NOT_ALLOWED` | 405 | 미허용 메서드 |
| `INTERNAL_ERROR` | 500 | 서버 오류(미처리 예외는 500으로 덮고 상세는 로그만) |

- 각 도메인 문서의 에러 표에는 **그 엔드포인트에서 실제로 날 수 있는** 코드만 적는다. 위 공통 코드는 전 엔드포인트 공통 전제(400 검증·401 인증·500 서버)라 반복 표기하지 않을 수 있다.

## 5. 각 도메인 문서 예시 읽는 법

각 API는 아래 4블록으로 기술한다. 예시는 그대로 복붙 가능한 실제 형태로 적는다.

1. **요청 예시** — `http` 블록. 메서드·전체 경로(쿼리 포함)·필요 헤더·바디까지.
   ```http
   POST /api/v1/records HTTP/1.1
   Host: api.modi.app
   Authorization: Bearer {accessToken}
   Content-Type: application/json

   { "exhibitionId": 51, "writeMode": "DIRECT", "content": "…", "emotionCodes": ["차분한"] }
   ```
2. **성공 응답 (200)** — `ApiResponse` **봉투 전체**(`meta`+`data`)를 보여준다.
3. **에러 응답 예시** — 대표 실패 1건을 봉투 전체 JSON으로 보여준다.
4. **에러 표** — 그 엔드포인트에서 날 수 있는 `errorCode`·HTTP·발생 조건 전체.

> 인증이 필요한(🔒/🔓) 요청은 `Authorization: Bearer {accessToken}` 헤더 또는 access 쿠키를 함께 보낸다. 예시에서는 헤더로 표기한다.
> 쿼리 파라미터의 한글·콤마는 실제 전송 시 URL 인코딩되지만(예: `region=SEOUL%2CGYEONGGI`), 문서 예시는 가독성을 위해 원문으로 적는다.

## 6. 상태 표기

- ✅ 기존 구현 유지 · 🔧 기존 구현 변경/확장 · 🆕 신규 개발
