# support — 파일 업로드 🆕

> 공통 규약: [공통 규약](README.md). 화면: [04] 포스터/미디어 추가 · [06] 프로필 이미지.
> 전시 포스터·기록 사진/영상·프로필 이미지 업로드 → URL 반환. 이후 각 도메인 요청 바디에 URL을 담아 전송.

## API 목록

| # | 기능 | Method | Path | 인증 | 상태 |
|---|---|---|---|---|---|
| 10.1 | 파일 업로드 | POST | `/api/v1/files` | 🔒 | 🆕 |

---

## 10.1 파일 업로드 `POST /api/v1/files` 🆕

`multipart/form-data`. 폼 필드 `file`(바이너리) + `purpose`.
- `purpose`: `EXHIBITION_POSTER` | `RECORD_MEDIA` | `PROFILE_IMAGE`
- 용량: PHOTO ≤10MB, VIDEO ≤100MB(기록 미디어 정책과 동일).

**요청 예시**
```http
POST /api/v1/files HTTP/1.1
Host: api.modi.app
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data; boundary=----modiBoundary

------modiBoundary
Content-Disposition: form-data; name="purpose"

RECORD_MEDIA
------modiBoundary
Content-Disposition: form-data; name="file"; filename="scene.jpg"
Content-Type: image/jpeg

(바이너리 데이터)
------modiBoundary--
```

**성공 응답 (200)**
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { "url": "https://cdn.modi.app/records/tmp/1.jpg", "type": "PHOTO", "sizeBytes": 2048000 }
}
```

**에러 응답 예시** (용량 초과)
```json
{ "meta": { "result": "FAIL", "errorCode": "FILE_TOO_LARGE", "message": "허용 용량을 초과했습니다." }, "data": null }
```

**에러 표**

| errorCode | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_INPUT` | 400 | file 누락, 미정의 purpose |
| `FILE_TOO_LARGE` 🆕 | 400 | 용량 초과 |
| `UNSUPPORTED_MEDIA_TYPE` 🆕 | 400 | 미지원 확장자/콘텐츠 타입 |
| `UNAUTHORIZED` | 401 | 미인증 |

> 영상 용량을 고려해 **presigned URL 발급 방식**으로 전환 가능(인프라 결정 후 확정). 그 경우 `POST /api/v1/files/presign` → 클라 직접 업로드 → 콜백/확정 흐름으로 스펙 변경.
