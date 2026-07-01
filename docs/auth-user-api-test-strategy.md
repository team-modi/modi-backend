# 인증·인가 / 유저 도메인 API 동작 테스트 전략

> 대상: `auth`(인증·인가) · `user`(유저) 도메인의 **실제 API 동작 검증**.
> 성격: 전략/기획 문서. "어떤 동작 테스트 전략이 있는지 → 비교 → 채택 계획"을 담는다.
> 실제 테스트 코드 구현은 본 문서 범위가 아니다(제안까지만).

---

## 1. 목적 & 범위

### 검증 목표
- 인증/인가 API(`login`·`refresh`·`logout`)와 유저 API(`me`·`profile`)가 **요청 → 응답까지 실제로 올바르게 동작**하는지 검증.
- 컨트롤러 · `@Authentication` 리졸버 · JWT 발급/검증 · 쿠키 · 전역 예외 · DB(영속화)까지 **실경로**로 태운다.
- 정상 경로뿐 아니라 **경계값·에러 코드·인가 실패**까지 커버.

### 검증 방식(확정 요구)
- **자동화 테스트** 중심: JUnit5 + MockMvc + Testcontainers-MySQL.
- local/test 프로파일에 **더미 데이터(User·SocialAccount) 시딩** 후 서버 기동 가능하게.
- **FE/브라우저 연동은 하지 않는다.** 대신 서버 쪽에서 **실제 API를 직접 호출**(MockMvc 또는 `.http`).
- **실제 OAuth(카카오/구글 라이브)는 사용하지 않는다.** 외부 HTTP는 목킹하거나, 토큰을 우회 발급한다.

### 범위 밖(Non-goals)
- 실 카카오/구글 계정으로 인가코드를 받는 **라이브 OAuth E2E**.
- FE(React/Vercel) 화면 연동 · 브라우저 자동화.
- 부하/성능 테스트, 보안 취약점 스캔.

---

## 2. 대상 API 인벤토리

| # | 도메인 | 메서드 · 경로 | 요청 | 응답 | 인증 | 주요 에러코드 |
|---|---|---|---|---|---|---|
| 1 | auth | `POST /api/v1/auth/login/{provider}` | `AuthDto.LoginRequest(code, redirectUri)` | `TokenResponse(accessToken, user{...})` | 불필요(로그인/가입 겸용) | `UNSUPPORTED_PROVIDER`(400) · `INVALID_REDIRECT_URI`(400) · `OAUTH_COMMUNICATION_FAILED`(502) |
| 2 | auth | `POST /api/v1/auth/refresh` | 없음(`refresh_token` 쿠키) | `TokenResponse` | refresh 쿠키 | `NO_REFRESH_TOKEN`(401) · `INVALID_REFRESH_TOKEN`(401) |
| 3 | auth | `POST /api/v1/auth/logout` | 없음(`refresh_token` 쿠키) | `ApiResponse<Object>` | refresh 쿠키(없어도 idempotent) | — |
| 4 | user | `GET /api/v1/users/me` | 없음 | `UserDto.MeResponse`(+ tasteKeywords·stats 스텁) | `@Authentication` | `NO_ACCESS_TOKEN`(401) · `INVALID_ACCESS_TOKEN`(401) · `USER_NOT_FOUND`(404) |
| 5 | user | `PUT /api/v1/users/me/profile` | `UserDto.ProfileRequest`(모든 필드 optional·부분 갱신) | `UserDto.ProfileResponse` | `@Authentication` | `INVALID_NICKNAME`(400) · `INVALID_INPUT`(400, enum·거주지) |

**성공은 전부 200**(프로젝트 컨벤션, 201/PATCH 미사용). 로그인/리프레시 성공 시 `access_token`·`refresh_token`은 **HttpOnly 쿠키**로 내려가고, access는 본문에도 실어 비쿠키 클라이언트(모바일)와 호환한다.

**참고 파일**
- `interfaces/auth/AuthV1Controller.java` · `AuthV1ApiSpec.java` · `dto/AuthDto.java`
- `interfaces/user/UserV1Controller.java` · `UserV1ApiSpec.java` · `dto/UserDto.java`
- 에러코드: `domain/auth/AuthErrorCode.java` · `domain/user/UserErrorCode.java`

---

## 3. 동작 테스트 전략 리스트업 (핵심)

각 전략을 **설명 / 커버리지 / 장단점 / 이 프로젝트 적합도**로 정리한다.

### S1. 계층별 자동화 피라미드 (기반 전략)
- **설명**: CLAUDE.md 테스트 컨벤션에 따라 계층별로 검증을 나눈다.
  | 대상 | 방식 | 예시(기존) |
  |---|---|---|
  | Domain(Entity/VO/규칙) | 순수 단위(컨텍스트 X) | `UserTest`, `ProviderTest` |
  | Controller(검증·예외 매핑) | `@WebMvcTest` + `@MockitoBean` | `AuthV1ControllerTest` |
  | Facade 유스케이스 | 단위(Mockito) 또는 `@SpringBootTest` | `AuthFacadeTest`(목), `UserFacadeTest`(실 DB) |
  | E2E(전체 플로우) | `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers | `AuthFlowIntegrationTest`, `UserProfileIntegrationTest` |
- **커버리지**: 단위 규칙 → HTTP 계약 → 오케스트레이션 → 전체 경로. 피라미드의 밑변(단위)이 넓고 위(E2E)가 좁다.
- **장점**: 실패 지점이 계층으로 좁혀져 디버깅 빠름. 단위는 컨텍스트 없이 초고속.
- **단점**: 계층을 넘나드는 통합 결함은 E2E에서만 잡힌다.
- **적합도**: ★★★★★ — 이미 프로젝트 컨벤션·기존 테스트가 이 구조. **모든 신규 테스트는 이 피라미드에 배치한다.**

### S2. E2E 실 로그인 플로우 (OAuth 목킹 + MockMvc) — *인증 파이프라인 자체 검증*
- **설명**: `KakaoApi`/`GoogleApi` HTTP Interface 빈만 `@MockitoBean`으로 고정하고, `POST /auth/login/{provider}`부터 실제로 태워 **JWT 발급·쿠키·리졸버·리프레시 회전·로그아웃**까지 end-to-end 검증. (`AuthFlowIntegrationTest`가 레퍼런스)
- **커버리지**: 로그인 → `/me`(쿠키 인증) → `/refresh`(회전) → 온보딩(`PUT profile`) → `logout`. 신규 유저 생성 + 소셜계정 연결, 쿠키 `HttpOnly`/`SameSite`/`Max-Age`.
- **장점**: 인증 **로직 자체**를 진짜로 검증(토큰 서명·만료·타입·claims, 쿠키 폴백). 회귀 안전망으로 가장 강력.
- **단점**: 무겁다(컨텍스트+DB). 실행 시간·Docker 데몬 필요.
- **적합도**: ★★★★★ — auth 도메인의 **주력 E2E**. Provider별(카카오/구글) 최소 1개씩.

### S3. 인증 엔드포인트 MockMvc 시나리오 (더미 유저 시딩 + 토큰 직접 발급) — *유저 API 실동작 검증*
- **설명**: 로그인 플로우를 매번 태우지 않고, **더미 유저를 저장한 뒤 그 유저의 access 토큰을 `TokenProvider`로 직접 발급**해 Bearer(또는 쿠키)로 보낸다. (`RecordV1ControllerTest`가 레퍼런스)
  ```java
  User u = userRepository.save(User.createFromSocial("user1"));
  String bearer = "Bearer " + tokenProvider.issue(u, "kakao").accessToken();
  mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer)) ...
  ```
- **커버리지**: 유저 API(`/me`, `/profile`)의 정상·경계·에러를 **로그인 비용 없이** 빠르게. 인가(본인 데이터만) 검증.
- **장점**: OAuth 목킹 불필요, 준비 코드 최소, 케이스 다량 확장 쉬움. **더미 데이터 + 실 API 호출** 요구에 정확히 부합.
- **단점**: 로그인 파이프라인 자체는 검증 안 함(그건 S2 담당).
- **적합도**: ★★★★★ — user 도메인의 **주력 E2E**. 인증이 필요한 다른 도메인 테스트의 표준 패턴.

### S4. `.http` 수동/탐색 테스트 (로컬 기동 서버 대상)
- **설명**: `./gradlew bootRun`으로 로컬 서버를 띄우고 IntelliJ HTTP Client(`.http`)로 실제 요청을 쏜다. 브라우저 없이 **서버 API를 직접 호출**. (기존 `.http/user.http` 존재)
- **커버리지**: 개발 중 수동 탐색, 응답 형태 눈으로 확인, 프론트 연동 전 계약 검증.
- **장점**: 버전관리되는 요청 모음, 온보딩 문서 역할. 자동화 전 빠른 확인.
- **단점**: 수동(회귀 자동화 아님). **무OAuth 환경에서 토큰 확보 수단이 필요**(→ §4-C).
- **적합도**: ★★★★☆ — 보조 도구. `user.http` 확장 + `auth.http` 신규로 시퀀스 문서화.

### S5. (선택·기각 후보) RestAssured / `TestRestTemplate` 실포트 기동
- **설명**: `webEnvironment=RANDOM_PORT`로 실제 서블릿 포트를 열고 HTTP 클라이언트로 호출.
- **평가**: MockMvc가 이미 서블릿 스택 전체(리졸버·예외 핸들러 포함)를 태우므로 이 프로젝트에선 **추가 이득이 낮다**. 의존성만 늘고 느림.
- **적합도**: ★☆☆☆☆ — **채택하지 않음**. (진짜 네트워크 계층·필터 체인 검증이 필요해질 때만 재검토)

---

## 4. 인증 우회(테스트 토큰 확보) 전략 비교

> "실제 OAuth 없이 로그인이 필요한 테스트에서 access 토큰을 어떻게 확보하나?"에 대한 정면 답변.
> **핵심 사실: `X-User-Id` 헤더로 유저를 식별하던 스텁은 이미 폐기됐다.** 현재 `@Authentication`은 **유효한 JWT(쿠키 또는 Bearer)만** 받는다. (`AuthenticationArgumentResolver` — 쿠키 `access_token` 우선, 없으면 `Authorization: Bearer` 폴백)

| 전략 | 방법 | 신규 코드 | 무엇을 검증 | 용도 | 권고 |
|---|---|---|---|---|---|
| **A. TokenProvider 직접 발급** | 유저 저장 후 `tokenProvider.issue(user,"kakao").accessToken()` → Bearer/쿠키 | 0 | 유저·인가 API 본체(로그인 제외) | **자동화 기본값** | ✅ 채택 |
| **B. OAuth 목킹 + 실 로그인** | `@MockitoBean KakaoApi/GoogleApi` → `POST /auth/login/{provider}` → 쿠키/본문 토큰 추출 | 0 | **인증 파이프라인 자체**(발급·쿠키·회전) | auth E2E | ✅ 채택 |
| **C. 테스트 전용 로그인/시드 토큰 발급기** | local/test 프로파일 한정 엔드포인트(예: `POST /auth/dev-login`) 또는 기동 시 콘솔에 시드 유저 토큰 출력 | 소량(별도 작업) | — (토큰 확보 수단) | `.http` 로컬 **수동** 테스트(무OAuth) | 🔶 옵션(필요 시) |
| **D. X-User-Id 헤더 스텁** | 헤더에 userId만 넣으면 인증 통과 | (폐기됨) | 인증 로직을 **우회** = 미검증 | — | ❌ 안티패턴 |

**D를 쓰지 않는 이유**: 인증 로직(토큰 서명·만료·타입·claims)을 통째로 건너뛰어 "인증이 실제로 작동하는가"를 검증하지 못한다. 프로덕션과 다른 경로를 타므로 **거짓 안심**을 준다. `RecordV1ControllerTest.java`의 주석도 스텁 폐기를 명시(`X-User-Id 스텁 폐기`). — `RecordV1Controller.java`의 X-User-Id 문구는 **stale Swagger 설명**일 뿐 실제 동작하지 않으므로 정리 대상.

**권고 정리**
- 자동화에서 유저/인가 API 검증 → **A**.
- 인증 그 자체(로그인·리프레시·쿠키) 검증 → **B**.
- 로컬에서 서버 띄우고 `.http`로 무OAuth 수동 테스트 → **C 옵션 도입 검토**(prod 프로파일에서는 반드시 비활성).

### (참고) C안 도입 시 안전장치
- `@Profile({"local","test"})` 로만 빈 등록 → prod 클래스패스/프로파일에서 미노출.
- 실 토큰 발급 로직은 **기존 `TokenProvider` 재사용**(별도 발급 경로 금지 — 프로덕션과 동일 토큰).
- 문서/스웨거에서 dev 전용임을 명시.

---

## 5. Mock / 더미 데이터 시딩 전략

| 방식 | 설명 | 용도 | 상태 |
|---|---|---|---|
| (a) 인라인 `@BeforeEach` 시딩 | 테스트 메서드 진입 전 `userRepository.save(User.createFromSocial(...))` | 자동화 테스트(현행) | 사용 중 |
| (b) 재사용 픽스처/빌더 | `UserFixture.user("nick")`, `SocialAccountFixture...` 등 헬퍼로 중복 제거 | 자동화 테스트 가독성·중복 | **신규 제안** |
| (c) local/test 프로파일 시더 | `CommandLineRunner`(또는 `data.sql`)로 고정 유저·소셜계정 주입 후 기동 | `.http` 수동 테스트용 고정 유저 확보 | **신규 제안(옵션)** |

**설계 메모**
- 도메인 규칙상 유저 생성은 `User.createFromSocial(...)` 정적 팩토리를 통해야 함(생성자 직접 호출 금지). 픽스처도 이를 감싼다.
- 현재 **픽스처/빌더·시더·`data.sql`은 전무** — 인라인 시딩만 존재. (b)를 먼저 도입해 A·B 패턴 테스트의 준비 코드를 줄이는 것이 ROI 높음.
- **Testcontainers 주의**: MySQL 컨테이너는 JVM 세션 내 **공유**된다. 테스트 간 격리를 위해 `@BeforeEach`에서 관련 테이블 정리(`deleteAll`) 또는 `@Transactional` 롤백 전략을 명시. soft-delete(`deleted_at`) 때문에 조회는 살아있는 행만 나오는 점을 픽스처가 인지.
- (c) 시더는 자동화 테스트에는 쓰지 않는다(테스트는 자기 데이터를 직접 시딩해 격리 유지). 오직 로컬 수동용.

---

## 6. 시나리오 매트릭스

각 케이스에 **기대 상태코드·에러코드**를 명시한다. (성공은 모두 200)

### 6.1 auth — 로그인 `POST /auth/login/{provider}`
| # | 시나리오 | 기대 |
|---|---|---|
| A1 | 신규 유저: 소셜 최초 로그인 → User+SocialAccount 생성 | 200, `profileCompleted=false`, 토큰·쿠키 발급 |
| A2 | 기존 유저: 동일 `(provider, providerUserId)` 재로그인 → 기존 User 재사용, email 갱신 | 200, 동일 userId |
| A3 | 이메일 미동의(email=null)로 로그인 | 200, `user.email=null` |
| A4 | 지원하지 않는 provider(`/auth/login/naver`) | 400 `UNSUPPORTED_PROVIDER` |
| A5 | 화이트리스트 밖 `redirectUri` | 400 `INVALID_REDIRECT_URI` |
| A6 | OAuth 통신 실패(목이 예외) | 502 `OAUTH_COMMUNICATION_FAILED` |
| A7 | `code`/`redirectUri` 누락(@Valid) | 400 검증 에러(fieldErrors) |
| A8 | 구글 provider로 로그인(플랫 응답 구조) | 200 |

### 6.2 auth — 리프레시 `POST /auth/refresh`
| # | 시나리오 | 기대 |
|---|---|---|
| R1 | 유효한 refresh 쿠키 → 새 access·refresh 발급(회전) | 200, 이전 refresh 무효화 |
| R2 | refresh 쿠키 없음 | 401 `NO_REFRESH_TOKEN` |
| R3 | 위조/만료/타입 불일치 refresh | 401 `INVALID_REFRESH_TOKEN` |
| R4 | 회전 후 **옛 refresh** 재사용 | 401 `INVALID_REFRESH_TOKEN` |

### 6.3 auth — 로그아웃 `POST /auth/logout`
| # | 시나리오 | 기대 |
|---|---|---|
| L1 | 로그인 상태에서 로그아웃 → 쿠키 만료(Max-Age=0), refresh 폐기 | 200 |
| L2 | refresh 없이 로그아웃(idempotent) | 200 |
| L3 | 로그아웃 후 그 refresh로 refresh 시도 | 401 `INVALID_REFRESH_TOKEN` |

### 6.4 인가/토큰 공통 (`/users/me` 등 보호 리소스로 검증)
| # | 시나리오 | 기대 |
|---|---|---|
| Z1 | 토큰 없음 | 401 `NO_ACCESS_TOKEN` |
| Z2 | 위조 서명/만료 access | 401 `INVALID_ACCESS_TOKEN` |
| Z3 | refresh 토큰을 access 자리에 사용(type 불일치) | 401 `INVALID_ACCESS_TOKEN` |
| Z4 | 쿠키 인증 경로(access_token 쿠키)로 접근 | 200 |
| Z5 | Bearer 헤더 경로로 접근 | 200 |

### 6.5 user — 내 프로필 `GET /users/me`
| # | 시나리오 | 기대 |
|---|---|---|
| M1 | 로그인 유저 프로필 조회 | 200, 프로필 필드 + `tasteKeywords=[]`·`stats=0`(스텁) |
| M2 | 토큰의 userId가 DB에 없음(삭제 등) | 404 `USER_NOT_FOUND` |

### 6.6 user — 프로필 수정 `PUT /users/me/profile`
| # | 시나리오 | 기대 |
|---|---|---|
| P1 | 닉네임만 부분 갱신(나머지 유지) | 200, 다른 필드 불변 |
| P2 | 전체 필드(닉네임·이미지·연령대·지역·구군) 갱신 | 200, `profileCompleted=true` |
| P3 | 빈/공백 닉네임(`"   "`) | 400 `INVALID_NICKNAME` |
| P4 | 닉네임 21자 초과 | 400 `INVALID_NICKNAME` |
| P5 | region 없이 district만 | 400 `INVALID_INPUT` |
| P6 | 잘못된 `ageGroup`/`residenceRegion` enum 문자열 | 400 `INVALID_INPUT` |
| P7 | 빈 바디(모든 필드 null) 갱신 | 200(부분 갱신 규칙 — 필드 유지, `profileCompleted` 처리 확인) |

> **주의**: 도메인 불변식(닉네임 1~20자·공백 금지, 거주지 orphan district 금지)은 **Entity `updateProfile()` 시점**에서 검증된다. 형식 검증(@NotNull/@Size)은 Request DTO. 같은 규칙을 양쪽에 중복하지 않도록 테스트도 "형식 오류=DTO", "불변식 위반=엔티티 경로"로 구분해 배치.

---

## 7. 채택 계획 (단계)

### Phase 1 — 자동화 커버리지 보강 (최우선)
- 패턴 **A**(TokenProvider 직접 발급)로 유저 API 시나리오(§6.5·6.6) 미커버 케이스 추가.
- 패턴 **B**(OAuth 목킹 + 실 로그인)로 auth 시나리오(§6.1~6.3)와 인가 공통(§6.4) 보강. 구글 provider 최소 1케이스.
- **산출물**: 확장된 `AuthFlowIntegrationTest`·`UserProfileIntegrationTest`(또는 시나리오별 신규 통합 테스트).
- **완료기준**: §6 매트릭스의 각 행이 최소 1개 자동화 테스트로 매핑. `./gradlew test` green.

### Phase 2 — 픽스처 & (옵션) 시더 도입
- `UserFixture`/`SocialAccountFixture` 빌더로 시딩 중복 제거(§5-b).
- 필요 시 local/test 프로파일 시더(§5-c) + 테스트 전용 로그인/토큰 발급기(§4-C) 도입.
- **완료기준**: 신규 테스트가 픽스처 재사용. 시더는 `local`에서만 활성(prod 미노출) 검증.

### Phase 3 — `.http` 세트 확장 (수동 탐색)
- `.http/user.http` 보완 + `.http/auth.http` 신규(로그인→me→refresh→logout 시퀀스, 에러 케이스 포함).
- 무OAuth 토큰 확보는 §4-C 경로로 문서화.
- **완료기준**: 로컬 기동 후 `.http`만으로 5개 엔드포인트 happy path + 대표 에러 재현 가능.

---

## 8. 실행 / 검증 방법

### 자동화 테스트
```bash
# Docker 데몬 필요(Testcontainers-MySQL). 전체 빌드+테스트
./gradlew build
# 테스트만
./gradlew test
# 특정 테스트
./gradlew test --tests "modi.backend.interfaces.AuthFlowIntegrationTest"
```
- Testcontainers는 `@Import(TestcontainersConfiguration.class)` + `@ServiceConnection`으로 MySQL 자동 기동(별도 `application-test.yml` 불필요).

### `.http` 수동 테스트 (로컬)
```bash
./gradlew bootRun          # local 프로파일로 기동 (http://localhost:8080)
```
1. (§4-C 채택 시) dev 로그인/시드 토큰으로 access 토큰 확보 → `.http` 변수에 대입.
   (미채택 시엔 카카오 인가코드가 필요하므로 로컬 수동은 B/C 중 하나가 사실상 전제.)
2. `.http/auth.http`·`user.http` 순서대로 실행하며 응답·상태코드 눈으로 검증.

### CI 연동 메모
- 기존 `ci.yml`이 PR에서 `gradle test`를 돌린다(Testcontainers 포함). Phase 1 신규 테스트는 자동으로 이 게이트에 포함된다.
- `.http`·시더·dev 로그인 엔드포인트는 CI 게이트가 아니라 **로컬 보조 도구**로 관리.

---

## 9. 부록

### 9.1 기존 테스트 인덱스 (이미 커버 중)
| 파일 | 유형 | 커버 |
|---|---|---|
| `domain/user/UserTest` | 순수 단위 | User 생성·닉네임 기본값·프로필 완료 규칙 |
| `domain/auth/ProviderTest` | 순수 단위 | Provider enum 파싱 |
| `application/auth/AuthFacadeTest` | Mockito 단위 | 로그인/리프레시 정책(의존 전부 목) |
| `application/user/UserFacadeTest` | `@SpringBootTest` | 프로필 부분 갱신 영속화 |
| `interfaces/auth/AuthV1ControllerTest` | `@WebMvcTest` | @Valid 검증·전역 예외 매핑·redirectUri 화이트리스트 |
| `interfaces/AuthFlowIntegrationTest` | E2E(Testcontainers) | 로그인→me→refresh→온보딩→logout, 쿠키·회전 |
| `interfaces/UserProfileIntegrationTest` | E2E(Testcontainers) | GET /me, PUT /profile 부분 갱신·검증 |
| `interfaces/record/RecordV1ControllerTest` | E2E(Testcontainers) | 패턴 A(토큰 직접 발급) 레퍼런스 |
| `CorsConfigTest` / `OpenApiDocsTest` | 통합/스모크 | CORS(prod) · OpenAPI 문서 생성 |

### 9.2 참고 파일 경로
- 인증 리졸버: `interfaces/auth/AuthenticationArgumentResolver.java`
- 토큰 포트/구현: `domain/auth/TokenProvider.java` · `infra/auth/JwtTokenProvider.java`
- 쿠키: `interfaces/auth/AuthCookie.java`
- OAuth 목 대상: `infra/auth/KakaoApi.java` · `GoogleApi.java` (HTTP Interface 빈)
- refresh 저장소: `domain/auth/RefreshTokenStore.java` · `infra/auth/InMemoryRefreshTokenStore.java`
- Testcontainers 설정: `src/test/java/modi/backend/TestcontainersConfiguration.java`
- 도메인 스펙: `.claude/skill/01_인증인가.md` · `02_유저.md`
- 테스트 컨벤션: `.claude/CLAUDE.md`(테스트 계층 표)

---

*작성 기준 코드베이스: 위 경로/엔드포인트/에러코드는 현행 소스에서 확인함. 도메인 확장(Exhibition 등) 시 `tasteKeywords`·`stats` 스텁이 실데이터로 바뀌면 §6.5 M1을 갱신할 것.*
