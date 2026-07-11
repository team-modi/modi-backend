# 관측(로그·메트릭·사용자 지표) 세팅 가이드

> 상태: **Phase 1(메트릭)·Phase 2(로그)·Phase 3(Metabase) 구현 완료** · 알림 가이드 포함 · 갱신 2026-07-11
> 대상 스택: Spring Boot 4 · EC2 단일 인스턴스 · `docker compose` · MySQL 8.4
> 방향(확정): **로그·메트릭 = Grafana Cloud(관리형 무료티어)**, **사용자 지표 = Metabase(DB 옆 셀프호스팅)**
>
> 자격증명 요약(.env / GitHub secrets):
> - 메트릭: `GC_PROM_URL`·`GC_PROM_USER`·`GC_API_KEY` — **등록 완료**
> - 로그(Loki): `GC_LOKI_URL`·`GC_LOKI_USER` — **추가 필요**(Grafana Cloud "Loki → Send Logs"에서 확보, API 키는 `GC_API_KEY` 재사용, scope에 `logs:write`)
> - 인스턴스 라벨: `GC_INSTANCE`(로컬 기본 `modi-backend-local`, 운영 `modi-backend-prod`)

## 0. 현재 상태 (관측 공백)
- 로그: 컨테이너 stdout만 → `docker compose logs`로만 조회, 재배포 시 유실, 검색/보존/알림 없음, 구조화(JSON) 안 됨.
- 메트릭: `spring-boot-starter-actuator`는 있으나 `health,info,metrics`만 노출, **Prometheus 엔드포인트 없음**.
- 사용자 지표: 없음(원천 데이터는 MySQL `users·records·reminds·exhibitions`에 이미 존재).

## 1. 목표 아키텍처

```
                 EC2 (docker compose)
  ┌───────────────────────────────────────────┐
  │  app(Spring)  ──/actuator/prometheus──┐    │        Grafana Cloud (관리형)
  │      │  stdout(JSON 로그)             │    │      ┌───────────────────────┐
  │      ▼                                ▼    │      │  Prometheus(메트릭)    │
  │   docker logs ── Alloy(에이전트) ──── remote_write ─▶  Loki(로그)          │
  │                    │  (컨테이너 로그 수집)   │      │  Grafana 대시보드/알림  │
  │                    └───────── loki.write ──────────▶└───────────────────────┘
  │                                            │
  │  MySQL ◀── Metabase(제품 분석, 내부망) ─────│  (사용자 지표: DAU/기록/리마인드…)
  └───────────────────────────────────────────┘
```

- **메트릭·로그**: EC2에 경량 에이전트 **Grafana Alloy** 한 개를 띄워 → 앱 `/actuator/prometheus` 스크랩 + 컨테이너 로그 수집 → **Grafana Cloud**로 remote_write. 자체 저장소 운영 없음.
- **사용자 지표**: Grafana Cloud가 사설 MySQL에 직접 붙기 번거로우므로(PDC 필요), **Metabase를 MySQL과 같은 EC2 내부망**에 두고 읽기 전용으로 붙인다. 비개발자용 대시보드.

## 2. Phase 1 — 성능 메트릭 (Micrometer → Grafana Cloud) ✅ 구현됨

### 2.1 앱 변경 (반영 완료)
- `build.gradle`: `runtimeOnly 'io.micrometer:micrometer-registry-prometheus'`
- `application.yaml`: `management.endpoints.web.exposure.include`에 `prometheus` 추가, `management.metrics.tags.application: modi-backend`

**검증 결과**(로컬 `18090`): `/actuator/prometheus` → **200, 139개 메트릭**
- `http_server_requests_seconds`(지연·처리량·에러) / `hikaricp_connections`(DB 풀) / `jvm_memory_used_bytes` / `process_cpu_usage` ✅
- 모든 지표에 `application="modi-backend"` 라벨 ✅
- `modi_ai_*`는 **첫 AI 호출 이후** 나타난다(Micrometer 지연 등록). AI 키 미설정이면 안 보이는 게 정상.

> ⚠️ **보안 부채(별도 과제)**: 운영에서 앱이 공개 8080이라 actuator도 공개된다. 단, `metrics`가 이미 공개돼 있어 `prometheus` 추가로 **노출 등급이 새로 생기지는 않는다**. 제대로 막으려면 `management.server.port` 분리(+루프백 바인딩)가 필요한데, deploy 헬스체크·k6·Postman·데모 프록시가 모두 `/actuator/health`를 앱 포트로 치고 있어 **함께 바꿔야 한다** → 별도 하드닝 PR로 분리.

### 2.2 Alloy 에이전트 (반영 완료)
- `observability/config.alloy` — `prometheus.scrape`(타깃 `app:8080/actuator/prometheus`, 15s) → `prometheus.remote_write`(Grafana Cloud).
- `compose.yaml`에 `alloy` 서비스 추가. **`obs` 프로파일**이라 자격증명 없이도 기존 `--profile app` 실행은 그대로 동작한다. Alloy UI(12345)는 루프백에만 바인딩.

**검증**: `alloy fmt` 문법 통과 ✅ · 내부망에서 `app:8080/actuator/prometheus` 도달(200) ✅

### 2.3 남은 작업 — Grafana Cloud 자격증명 연결 (사람이 해야 함)
1. [grafana.com](https://grafana.com) 무료 가입 → Stack 생성.
2. Stack의 **Prometheus** 항목에서 확보:
   - `GC_PROM_URL` = remote write endpoint (`https://prometheus-prod-XX-....grafana.net/api/prom/push`)
   - `GC_PROM_USER` = 인스턴스 ID(숫자)
   - `GC_API_KEY` = Access Policy Token (`metrics:write` 권한; Phase 2에서 `logs:write`도 추가)
3. 로컬 `.env`(gitignore)에 기록 후 실행:
   ```bash
   docker compose --profile app --profile obs up -d
   # Alloy 상태 확인: http://127.0.0.1:12345  (타깃 UP 여부)
   ```
### 2.4 운영(EC2) 적용 — `deploy.yml` 배선 완료 ✅
`deploy.yml`이 이미 관측을 지원하도록 수정됨(구현 완료):
- GitHub secrets `GC_PROM_URL`·`GC_PROM_USER`·`GC_API_KEY`를 SSH 세션으로 전달 → EC2 `.env`에 기록.
- **조건부 기동**: `GC_API_KEY`가 있으면 `--profile app --profile obs`(Alloy 포함), 없으면 `--profile app`만 → **secrets 등록 전에도 운영 배포가 깨지지 않는다.**

**사람이 할 일 = GitHub secrets 3개 등록** (이거만 하면 다음 배포부터 운영 메트릭이 흐름):
1. GitHub 레포 → **Settings → Secrets and variables → Actions → New repository secret**
2. 3개 등록(로컬 `.env`에 넣은 값과 동일):
   - `GC_PROM_URL` = `https://prometheus-prod-49-prod-ap-northeast-0.grafana.net/api/prom/push`
   - `GC_PROM_USER` = 인스턴스 ID(숫자)
   - `GC_API_KEY` = `glc_...` (metrics:write)
3. `main`에 배포가 나가면 EC2에서 Alloy가 뜨고 `instance="modi-backend-ec2"`로 전송 시작.
   - 로컬(`modi-backend-ec2`는 로컬 config에도 같은 라벨이라) 구분이 필요하면 운영/로컬 `instance` 라벨을 분리하는 것도 방법(선택).

> 검증(로컬): Alloy remote_write **성공(실패 0)**, Grafana Cloud 수신 확인, k6 부하 시 지표 이동 확인 완료.

### 2.4 대시보드
Grafana Cloud에서 커뮤니티 대시보드 import:
- **JVM (Micrometer)** — dashboard ID `4701`
- **Spring Boot Statistics / APM** — `http.server.requests` 기반(요청 지연·처리량·에러율). 커스텀 `modi.ai.tokens/calls` 패널도 추가.

> 대안(에이전트 없이): Spring OTel(`opentelemetry` 스타터)로 Grafana Cloud **OTLP 게이트웨이**에 메트릭·트레이스를 직접 push. 단, 컨테이너 stdout 로그 수집은 에이전트가 편해서 위 Alloy 방식을 기본으로 둔다.

## 3. Phase 2 — 운영 로그 (구조화 JSON → Grafana Cloud Loki)

### 3.1 구조화 로깅(JSON) + 요청 상관관계(requestId)
`build.gradle`:
```gradle
implementation 'net.logstash.logback:logstash-logback-encoder:8.0'  // 버전 최신 확인
```
`src/main/resources/logback-spring.xml`:
```xml
<configuration>
  <springProfile name="prod">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>requestId</includeMdcKeyName>
      </encoder>
    </appender>
    <root level="INFO"><appender-ref ref="JSON"/></root>
  </springProfile>
  <springProfile name="!prod">   <!-- 로컬은 사람이 읽는 기본 콘솔 -->
    <include resource="org/springframework/boot/logging/logback/base.xml"/>
  </springProfile>
</configuration>
```
요청마다 `requestId`를 MDC에 넣는 서블릿 필터(로깅용 — 인증 필터와 무관):
```java
@Component
public class RequestIdFilter extends OncePerRequestFilter {
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String id = Optional.ofNullable(req.getHeader("X-Request-Id")).filter(s -> !s.isBlank())
        .orElse(UUID.randomUUID().toString());
    MDC.put("requestId", id);
    res.setHeader("X-Request-Id", id);
    try { chain.doFilter(req, res); } finally { MDC.remove("requestId"); }
  }
}
```
> 나중에 분산 추적까지 원하면 Micrometer Tracing/OTel로 `traceId`·`spanId`를 MDC에 자동 주입(로그↔트레이스 연결).

### 3.2 Alloy로 컨테이너 로그 수집 → Loki
`config.alloy`에 추가:
```alloy
discovery.docker "containers" { host = "unix:///var/run/docker.sock" }
loki.source.docker "logs" {
  host       = "unix:///var/run/docker.sock"
  targets    = discovery.docker.containers.targets
  forward_to = [loki.write.gc.receiver]
}
loki.write "gc" {
  endpoint {
    url = env("GC_LOKI_URL")
    basic_auth { username = env("GC_LOKI_USER")  password = env("GC_API_KEY") }
  }
}
```
→ Grafana Cloud Explore(Loki)에서 `{container="modi-backend"} | json | level="ERROR"` 처럼 검색·알림.
> 대안: Alloy 없이 docker `awslogs`가 아닌 **loki docker log driver** 플러그인을 써도 되지만, 메트릭까지 Alloy 하나로 처리하는 게 단순하다.

## 4. Phase 3 — 사용자 지표(제품 분석) — Metabase

이미 DB에 원천이 다 있으니 **앱 코드 변경 없이** 시작한다. Grafana Cloud가 사설 MySQL에 붙기 번거로우므로 Metabase를 EC2 내부망에 둔다.

`compose.yaml`(내부 포트, 보안그룹으로 잠금):
```yaml
  metabase:
    image: metabase/metabase:latest
    profiles: ['analytics']         # 별도 프로파일로 필요할 때만
    ports: ['127.0.0.1:3001:3000']  # 외부 직접 노출 금지 → SSH 터널/VPN로 접근
    environment:
      - MB_DB_TYPE=h2               # 메타베이스 자체 저장소(초기엔 h2, 운영은 별도 DB 권장)
```
- MySQL에 **읽기 전용 계정** 만들어 데이터소스로 연결(운영 부하 회피 — 가능하면 read replica).
- 대표 지표 SQL(질문/대시보드로 저장):
```sql
-- 신규 가입 추이(일자별)
SELECT DATE(created_at) AS d, COUNT(*) AS signups FROM users GROUP BY d ORDER BY d;
-- 기록 생성 추이
SELECT DATE(created_at) AS d, COUNT(*) AS records
FROM records WHERE deleted_at IS NULL GROUP BY d ORDER BY d;
-- 리마인드 저장 수 & AI 상태 분포
SELECT ai_status, COUNT(*) FROM reminds WHERE deleted_at IS NULL GROUP BY ai_status;
-- 인기 감정 Top 10
SELECT emotion_code, COUNT(*) c FROM record_emotions GROUP BY emotion_code ORDER BY c DESC LIMIT 10;
-- 리마인드 전환율: 7일+ 지난 기록 중 회고된 비율
SELECT
  ROUND(100 * SUM(CASE WHEN r.record_id IS NOT NULL THEN 1 ELSE 0 END) / COUNT(*), 1) AS remind_rate_pct
FROM records rec
LEFT JOIN (SELECT DISTINCT record_id FROM reminds WHERE deleted_at IS NULL) r ON r.record_id = rec.id
WHERE rec.deleted_at IS NULL AND rec.created_at <= NOW() - INTERVAL 7 DAY;
```
> 더 본격적인 퍼널·리텐션·프론트 이벤트가 필요해지면 **PostHog**(셀프호스팅/클라우드)로 확장. 그때는 프론트 SDK + 백엔드 도메인 이벤트(기록 작성·리마인드 저장 등) 전송을 설계.

## 4.5 Metabase 접속 (구현 완료)
1. 읽기전용 DB 계정 1회 생성(비번을 강한 값으로 바꿔서):
   ```bash
   docker compose --profile app exec -T mysql mysql -uroot -p'verysecret' < observability/metabase-readonly-user.sql
   ```
   (`SELECT` 권한만 — 쓰기 거부 검증됨: DELETE 시 `command denied`)
2. `docker compose --profile app --profile analytics up -d` → `http://127.0.0.1:3001`(루프백, 외부 노출 금지 → SSH 터널/VPN).
3. Metabase 최초 세팅에서 데이터베이스 추가:
   - Type: **MySQL** / Host: **`mysql`**(도커 내부망) / Port: `3306` / DB: `mydatabase`
   - User: `metabase_ro` / Password: (위에서 설정한 값)
4. §4의 SQL을 "질문(SQL)"으로 저장 → 대시보드로 묶기.
> 검증(로컬): Metabase 컨테이너 health `ok`, 읽기전용 계정으로 앱 데이터 조회 성공·쓰기 거부 확인.

## 4.7 Metabase 외부 공개 — Cloudflare Tunnel (EC2 상시)
노트북 loopback으론 남이 못 본다. EC2에서 Metabase를 **Cloudflare Tunnel**로 HTTPS 공개해 팀이 상시 접속한다. 포트 개방/도메인 인증서 설치 없이 아웃바운드 커넥션만 쓴다(보안그룹 인바운드 불필요).

**코드 배선(완료)**:
- `compose`: `metabase`(analytics 프로파일) + `cloudflared`(tunnel 프로파일, `TUNNEL_TOKEN`으로 named tunnel 연결).
- `deploy.yml`: `CF_TUNNEL_TOKEN` 시크릿 있으면 배포 시 `--profile analytics --profile tunnel` 자동 기동(없으면 스킵 → 앱/관측만).
- `observability/metabase-setup.py`: 새 인스턴스에 관리자·DB연결·대시보드 재현(환경변수 기반, 시크릿 하드코딩 없음).

**사람이 할 일(Cloudflare)**:
1. Cloudflare 가입(무료) → **Zero Trust → Networks → Tunnels → Create a tunnel**(Cloudflared 방식) → 이름 지정 → **토큰 복사**(`eyJ...`).
2. 그 터널의 **Public Hostname** 추가:
   - Subdomain/Domain: 원하는 주소(예: `metabase` + `내도메인`) — ⚠️ **도메인이 Cloudflare에 등록돼 있어야 고정 주소가 됨**(도메인 없으면 §참고).
   - Service: **`HTTP`** / **`metabase:3000`** (같은 compose 네트워크 이름).
3. GitHub secret 등록: `CF_TUNNEL_TOKEN`(위 토큰), `MB_SITE_URL`(`https://metabase.내도메인`).
4. 배포(main) 나가면 EC2에서 `metabase`+`cloudflared` 기동 → 그 hostname으로 접속(관리자 로그인).
5. 첫 접속 후 `metabase-setup.py`를 EC2에서 1회 실행(또는 UI로 세팅)해 대시보드 생성:
   ```bash
   MB_URL=http://localhost:3001 MB_ADMIN_EMAIL=you@ex.com MB_ADMIN_PW='...' MB_RO_PW='...' \
     python3 observability/metabase-setup.py
   ```

> **도메인 없으면 (EC2 quick tunnel)**: named tunnel의 고정 hostname엔 도메인이 필요하다. 도메인이 없으면 **quick tunnel을 EC2에서** 돌려 도메인 없이 상시 공개할 수 있다 — GitHub secret `CF_QUICK_TUNNEL=true`로 켜면 `cloudflared-quick`(tunnel-quick 프로파일)이 metabase를 임시 HTTPS URL로 노출한다.
> - ⚠️ **URL이 재시작마다 바뀐다**(배포·재기동 시 새 주소) + Cloudflare 운영 비권장. "항상 켜짐"은 되지만 "고정 주소"는 아니다.
> - URL 확인: 배포 로그 마지막 줄, 또는 `docker logs modi-cloudflared-quick | grep trycloudflare`.
> - **고정 주소가 필요하면** 싼 도메인(~1만원/년)을 Cloudflare에 추가 → `CF_TUNNEL_TOKEN`(named)로 전환.
> **EC2 리소스**: Metabase는 유휴 시에도 ~1GB 메모리를 쓴다. 앱과 같은 소형 인스턴스면 부하 시 경쟁하니, 트래픽 커지면 분석용 소형 인스턴스로 분리 권장.
> **저장소**: 단일 관리자면 H2(볼륨)로 충분. 여러 명 동시 사용 시 MySQL 앱DB로 분리(전용 `metabase` DB + 쓰기 계정).

## 4.6 알림(Alerts) — Grafana Cloud UI (권장 5분 세팅)
메트릭·로그가 흐르면, "문제를 사람이 안 보고 있어도 알려주게" 알림을 건다. Grafana Cloud는 관리형 alerting이라 **UI로 만든다**.

**만드는 곳**: 좌측 **Alerts & IRM → Alert rules → New alert rule**. 데이터소스는 `grafanacloud-...-prom`.

권장 초기 알림 3개(조건 PromQL / 임계 / 의미):
| 알림 | 조건(PromQL) | 발화 기준 |
|---|---|---|
| **DB 커넥션 대기** | `max(hikaricp_connections_pending{application="modi-backend"})` | `> 0` 이 **5분 지속** → 풀 부족(느려짐 원인) |
| **높은 지연(p95)** | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="modi-backend"}[5m])) by (le))` | `> 1`(초) 5분 지속 |
| **에러율 급증** | `sum(rate(http_server_requests_seconds_count{application="modi-backend",outcome="SERVER_ERROR"}[5m])) / clamp_min(sum(rate(http_server_requests_seconds_count{application="modi-backend"}[5m])),0.001)` | `> 0.05`(5%) 5분 지속 |
| (앱 다운) | `up{job="modi-backend"}` | `== 0` 2분 지속 |

**단계**: New alert rule → 위 쿼리 입력 → "Expression"에서 임계(예: `IS ABOVE 0`) → "for" = `5m` → **Contact point**(이메일/Slack) 지정 → 저장.
- Contact point는 **Alerts & IRM → Contact points**에서 이메일/Slack webhook 등록.
- 로그 기반 알림도 가능(Loki): `count_over_time({service="modi-backend"} | json | level="ERROR" [5m]) > 10`.

## 5. 보안·운영 주의
- **actuator/prometheus 공개 금지**: 호스트 공개 포트로 매핑하지 말 것. Alloy가 도커 내부망(`app:8080`)에서만 스크랩. 필요하면 별도 management 포트 + 보안그룹/IP 허용목록.
- **Metabase/Grafana 접근 제한**: 외부 직접 노출 금지(보안그룹·SSH 터널·VPN). Metabase 관리자 계정 강암호.
- **시크릿**: `GC_API_KEY` 등은 `.env`(gitignore) + GitHub Actions secrets. deploy.yml이 `.env`에 기록하는 기존 방식 재사용.
- **단일 EC2 리소스**: 부하테스트에서 CPU 바운드 확인됨. Alloy는 가볍지만 Metabase는 메모리를 좀 먹는다(수백 MB) → 트래픽 커지면 관측/분석용 별도 소형 인스턴스로 분리.
- **로그 유실/디스크**: Grafana Cloud로 보내더라도 로컬 docker json-file 로테이션은 걸어두기(`compose`의 각 서비스에 `logging.options.max-size/max-file`).

## 6. 무료 한도(대략 — 가입 시 최신값 확인)
- **Grafana Cloud Free**: 활성 시계열 ~10k, 로그 ~50GB, 보존 ~14일, 시트 3명 수준. 초기 트래픽엔 충분.
- **Metabase**: 오픈소스 셀프호스팅 = 무료(컨테이너 리소스만).

## 7. 실행 체크리스트
**Phase 1 (메트릭)**
- [x] `micrometer-registry-prometheus` 추가 + `management...include`에 `prometheus` + `application` 라벨
- [x] `observability/config.alloy` + `compose`에 `alloy` 서비스(`obs` 프로파일)
- [x] Grafana Cloud 가입 → Prometheus 토큰 확보 → `.env`/GitHub secrets 등록 (완료)
- [x] `deploy.yml`에 `GC_*` 주입 + 배포 시 `--profile obs` 조건부 활성화
- [ ] Grafana 대시보드 import(JVM 4701 + Spring HTTP) → p95/처리량/에러/AI 토큰 확인

**Phase 2 (로그)**
- [x] `logstash-logback-encoder` + `logback-spring.xml`(prod=JSON) + `RequestIdFilter`
- [x] Alloy에 `loki.source.docker`/`loki.write` + docker.sock 마운트 (Loki 자격증명 `GC_LOKI_*` 추가 시 전송 시작)
- [x] compose 로그 로테이션(`x-logging` 앵커, max-size 10m/max-file 3)

**Phase 3 (사용자 지표)**
- [x] `metabase` 서비스 + MySQL 읽기전용 계정(`metabase_ro`) + 핵심 지표 SQL (대시보드는 UI에서)

**하드닝(별도)**
- [ ] actuator를 `management.server.port`로 분리 + 루프백 바인딩 (deploy 헬스체크·k6·Postman·데모 프록시 동시 수정 필요)
- [ ] Metabase/Alloy UI 접근을 보안그룹/SSH 터널로 잠금

## 8. 권장 착수 순서
1. **메트릭(Phase 1)** — 앱 변경 최소(의존성+설정 2줄)로 상시 성능 관측을 가장 빨리 확보.
2. **사용자 지표(Metabase)** — 앱 변경 0, 제품 의사결정에 바로 쓰임.
3. **로그(Phase 2)** — 구조화+수집. 인시던트 대응 강화.
