# exhibition — 전시 (전시관 포함)

> 공통 규약: [공통 규약](../공통/README.md). 화면: [01] 홈 · [02] 전시 탐색 · [03] 전시 상세.
> 홈은 단일 집계가 아니라 **배너 1콜 + 섹션 3콜(목록 API 재사용) = 병렬 4콜**.

## 공통 코드

| 코드 | 값 |
|---|---|
| region | `SEOUL` `GYEONGGI` `INCHEON` `DAEGU` `GYEONGBUK` `BUSAN` `ULSAN` `GYEONGNAM` `SEJONG` `JEONNAM` `JEONBUK` `JEJU` `CHUNGNAM` `CHUNGBUK` `ETC` (15종 확정, 그룹핑 없음) |
| category 🔧 | 기존 `PAINTING` `PHOTO` `MEDIA` `SCULPTURE` `ETC` + 신규 `DESIGN` `CRAFT` `ARCHITECTURE` `PERFORMANCE` |
| exhibitionForm 🆕 | `SOLO`(개인전) `GROUP`(단체전) `CURATED`(기획전) `ART_FAIR`(아트페어) |
| section 🆕 | `ending-soon` `opening-this-month` `free` |
| sort | `latest`(기본) `ending` `popular`(조회수·확정) `distance` 🆕 |

**정렬 동률 타이브레이커**: 모든 정렬에서 동률이면 이름 가나다순, **첫 글자 한글이 영문보다 우선**.

## 목록 항목 공통 스키마 `ExhibitionListItem` 🔧
5.1~5.2, 관심 전시 목록이 공유하는 카드 항목.
```json
{
  "exhibitionId": 51, "type": "CATALOG",
  "title": "희박한 공기 Thin Air",
  "posterUrl": "https://cdn.modi.app/exhibitions/51/poster.jpg",
  "startDate": "2026-07-01", "endDate": "2026-08-15",
  "place": "동작아트갤러리/서울", "region": "SEOUL", "category": "PAINTING",
  "artistSummary": "김미경 외 10인",  // 🆕
  "dDay": 5,                          // 🆕 종료 D-n(종료 후 null)
  "free": true,                       // 🆕 무료 여부
  "bookmarked": false                 // 🆕 로그인 시 관심 여부(비로그인 false)
}
```

## API 목록

| # | 기능 | Method | Path | 인증 | 상태 |
|---|---|---|---|---|---|
| 5.1 | 홈 배너 조회 | GET | `/api/v1/exhibitions/banners` | 🔓 | 🆕 |
| 5.2 | 전시 목록/탐색/섹션 | GET | `/api/v1/exhibitions` | 🔓 | 🔧 |
| 5.3 | 전시 상세 | GET | `/api/v1/exhibitions/{exhibitionId}` | 🔓 | 🔧 |
| 5.4 | 전시 직접 추가(CUSTOM) | POST | `/api/v1/exhibitions/custom` | 🔒 | 🔧 |
| 5.5 | 전시관 검색(자동완성) | GET | `/api/v1/venues` | 🔒 | 🆕 |

---

## 5.1 홈 배너 조회 `GET /api/v1/exhibitions/banners` 🆕

캐러셀 최대 3개. 운영자 지정 전시 우선, 부족분은 진행 중 전시 조회수 상위로 채움(노출 순서대로).

**요청 예시**
```http
GET /api/v1/exhibitions/banners HTTP/1.1
Host: api.modi.app
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "banners": [
      {
        "exhibitionId": 3,
        "title": "인상주의를 넘어: 르누아르, 드가, 고흐, 마티스",
        "bannerImageUrl": "https://cdn.modi.app/banners/3.jpg",
        "startDate": "2026-05-28", "endDate": "2026-08-23",
        "place": "세종문화회관 미술관 1,2관"
      }
    ]
  }
}
```
- 배너 없으면 `"banners": []`(에러 아님).

**에러 응답 예시** (서버 오류)
```json
{ "meta": { "result": "FAIL", "errorCode": "INTERNAL_ERROR", "message": "서버 오류가 발생했습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INTERNAL_ERROR` | 500 | 배너 조회 중 서버 오류 |

---

## 5.2 전시 목록/탐색/섹션 `GET /api/v1/exhibitions` 🔧

탐색 화면 + 홈 섹션 미리보기(`section=X&size=2`) + 섹션 전체보기(`section=X&size=20`) 공용.

**요청 Query**

| 파라미터 | 값/규칙 |
|---|---|
| `keyword` | 전시명 부분 일치, **최소 2글자**(1글자 400). 대소문자·공백 무시. 필터 집합 내 AND 재검색 |
| `section` 🆕 | `ending-soon` \| `opening-this-month` \| `free` |
| `period` 🆕 | `month`(기본) \| `week` — `section=opening-this-month`용 |
| `region` 🔧 | 콤마 다중(`SEOUL,GYEONGGI`) |
| `category` 🔧 | 콤마 다중 |
| `date` | `YYYY-MM-DD` — 해당일 관람 가능 전시 |
| `sort` | `latest`(기본) \| `ending` \| `popular` \| `distance` 🆕 |
| `lat`,`lng` 🆕 | `sort=distance` 필수(프론트가 항상 제공, 서버 fallback 없음) |
| `cursor`,`size` 🔧 | 커서 페이지네이션 |

**요청 예시 1 — 탐색(검색어 + 다중 필터)**
```http
GET /api/v1/exhibitions?keyword=빛의&region=SEOUL,GYEONGGI&category=PHOTO,MEDIA&sort=latest&size=20 HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**요청 예시 2 — 홈 "곧 끝나기" 섹션 미리보기**
```http
GET /api/v1/exhibitions?section=ending-soon&size=2 HTTP/1.1
Host: api.modi.app
```

**요청 예시 3 — 거리순 전체보기(다음 페이지)**
```http
GET /api/v1/exhibitions?section=free&sort=distance&lat=37.5033&lng=126.9575&size=20&cursor=eyJzb3J0IjoiZGlzdGFuY2UiLCJsYXN0SWQiOjUxfQ HTTP/1.1
Host: api.modi.app
```

**성공 응답 (200)** — `CursorResponse<ExhibitionListItem>`
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
        "dDay": 5, "free": true, "bookmarked": false
      }
    ],
    "nextCursor": "eyJzb3J0IjoibGF0ZXN0IiwibGFzdElkIjo1MX0",
    "hasNext": true,
    "totalCount": 160
  }
}
```
- 검색 결과 0건이면 `"content": []`, `"totalCount": 0`, `"nextCursor": null`(빈 상태 UI는 프론트).

**에러 응답 예시** (거리순인데 좌표 없음)
```json
{ "meta": { "result": "FAIL", "errorCode": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_INPUT` | 400 | keyword 1글자 / `sort=distance`인데 lat·lng 없음 / 미정의 region·category·section |
| `INVALID_CURSOR` | 400 | 커서-조건 불일치 |
| `EXTERNAL_API_UNAVAILABLE` | 503 | (외부 전시 API 실시간 연동 경로 사용 시) 수집 원본 장애 |

---

## 5.3 전시 상세 `GET /api/v1/exhibitions/{exhibitionId}` 🔧

CATALOG는 공개, CUSTOM은 등록자 본인만(타인 403). 조회 시 `viewCount` 증가.

**요청 예시**
```http
GET /api/v1/exhibitions/51 HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "exhibitionId": 51, "type": "CATALOG", "title": "희박한 공기 Thin Air",
    "posterUrl": "https://cdn.modi.app/exhibitions/51/poster.jpg",
    "startDate": "2026-07-01", "endDate": "2026-08-15", "place": "동작아트갤러리/서울",
    "region": "SEOUL", "category": "PAINTING",
    "description": "한국에서 태어나 네덜란드에서 성장했으며…",
    "operatingHours": "월/화/목/금/일 10:00-18:00, 수/토 10:00-21:00",
    "price": "무료", "artists": ["김미경"], "artistSummary": "김미경 외 10인",
    "keywords": ["회화·드로잉"], "serviceName": "문화포털", "detailUrl": "https://culture.go.kr/exhibitions/51",
    "gpsX": 126.9575, "gpsY": 37.5033, "address": "서울 동작구 상도로 395",
    "imgUrl": null, "phone": null, "viewCount": 1024, "sigungu": "동작구", "placeUrl": null,
    "free": true, "bookmarked": true, "recorded": false
  }
}
```
- 위치확인 바텀시트는 `gpsX/gpsY/place/address` 재사용(추가 API 없음). `recorded=true`면 "기록하기" 버튼 분기.

**에러 응답 예시** (없는 전시)
```json
{ "meta": { "result": "FAIL", "errorCode": "NOT_FOUND", "message": "요청한 전시를 찾을 수 없습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `NOT_FOUND` | 404 | 없는 전시(`EXHIBITION_NOT_FOUND`) |
| `FORBIDDEN` | 403 | 타인의 CUSTOM 전시 접근 |

---

## 5.4 전시 직접 추가 `POST /api/v1/exhibitions/custom` 🔧

기록 플로우의 "전시 직접 추가하기". 포스터는 먼저 [파일 업로드](../공통/파일%20업로드%20support.md)로 URL 확보.

**요청 예시**
```http
POST /api/v1/exhibitions/custom HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "title": "조용한 호숫가",
  "posterUrl": "https://cdn.modi.app/exhibitions/tmp/abc.jpg",
  "venueId": 7,
  "place": "아리랑 문화관",
  "startDate": "2026-06-24",
  "endDate": "2026-07-31",
  "exhibitionForm": "SOLO",
  "artistName": "김선명",
  "region": "SEOUL",
  "category": "PHOTO"
}
```
- `venueId`(전시관 검색 선택)와 `place`(직접 입력)는 둘 중 하나. `exhibitionForm=SOLO`면 `artistName` 필수("개인전 · 김선명").

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "exhibitionId": 108 }
}
```

**에러 응답 예시** (종료일이 시작일보다 빠름)
```json
{ "meta": { "result": "FAIL", "errorCode": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_INPUT` | 400 | title 공백/초과, 날짜 파싱 실패, endDate<startDate, 미정의 region·category·exhibitionForm |
| `VENUE_NOT_FOUND` 🆕 | 404 | 없는 venueId 지정 |
| `UNAUTHORIZED` | 401 | 미인증 |

---

## 5.5 전시관 검색 `GET /api/v1/venues` 🆕

직접 추가 시 전시관명 자동완성. 상위 20개 고정(커서 미적용).

**요청 예시**
```http
GET /api/v1/venues?keyword=아 HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "venues": [
      { "venueId": 7, "name": "아리랑 문화관", "address": "서울 성북구 …", "region": "SEOUL" },
      { "venueId": 12, "name": "아시아 현대미술관", "address": "서울 …", "region": "SEOUL" }
    ]
  }
}
```
- keyword 빈 값이면 `"venues": []`(에러 아님).

**에러 응답 예시** (미인증)
```json
{ "meta": { "result": "FAIL", "errorCode": "UNAUTHORIZED", "message": "인증이 필요합니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `UNAUTHORIZED` | 401 | 미인증 |
