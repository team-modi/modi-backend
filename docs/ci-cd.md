# CI/CD 구축 결과

### TL;DR

> GitHub Actions로 **`main` 푸시 시 EC2 자동 배포(CD)** 와 **`main`·`develop` PR마다 테스트 실행(CI)** 파이프라인을 구성하고, 두 브랜치에 **"CI 통과해야 머지 가능"** 브랜치 보호 규칙을 적용했다. 배포는 **테스트 통과 후에만** 실행되며, **배포 후 헬스체크로 정상 기동을 검증**한다. 실제 워크플로우 실행이 모두 성공(초록불)함을 확인했다.

---

### 📌 전체 흐름

```
코드 작업
  └─ PR (→ main / develop)
        └─ [CI] ci.yml : ./gradlew test 실행  ← 통과해야 머지 가능 (브랜치 보호)
              └─ main 머지(push)
                    └─ [CD] deploy.yml
                          ├─ (1) test : ./gradlew test           ← 실패 시 배포 안 함
                          └─ (2) deploy : EC2 SSH → 재배포 → 헬스체크 UP 확인 (실패 시 배포 실패 처리)
```

- **CI** = 변경이 테스트를 깨지 않는지 검증하는 **머지 게이트**
- **CD** = `main`에 반영되면 **테스트 통과 시 자동 배포**, 배포 후 **헬스체크로 검증**

---

### 📌 CD — 자동 배포 (`.github/workflows/deploy.yml`)

- **트리거** : `main`에 push/merge (수동 실행 `workflow_dispatch`도 지원)
- **구조** : `test` → `deploy` (`deploy`는 `needs: test`라 **테스트 통과해야만 실행**)
- **deploy 동작** : `appleboy/ssh-action`으로 EC2 접속 → 아래 스크립트 실행

```bash
cd ~/modi-backend
git fetch origin main
git checkout -B main origin/main
git reset --hard origin/main
sudo docker compose --profile app up -d --build

# 배포 후 헬스체크 검증 (UP 될 때까지 최대 150초 폴링, 실패 시 로그 출력 후 배포 실패)
for i in $(seq 1 30); do
  curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' && exit 0
  sleep 5
done
exit 1
```

- **필요 시크릿** (GitHub → Settings → Secrets and variables → Actions)
  - `EC2_HOST` = `3.35.111.143`
  - `EC2_USER` = `ec2-user`
  - `EC2_SSH_KEY` = 배포 전용 SSH **개인키** (공개키는 EC2 `~/.ssh/authorized_keys`에 등록)
- **결과** : `main` 머지 → test 통과 → 재배포 → 헬스체크 `{"status":"UP"}` 확인 ✅
- **효과** : 테스트 깨진 코드는 배포되지 않고, 배포 후 앱이 안 뜨면 **워크플로우가 빨간불로 즉시 인지** 가능

---

### 📌 CI — 테스트 (`.github/workflows/ci.yml`)

- **트리거** : `main`·`develop`로 가는 **PR** (두 브랜치 모두 PR 필수라 push 트리거는 불필요)
- **동작** : JDK 21 셋업 → `./gradlew test` 실행
- **테스트 환경** : 이 프로젝트 테스트는 **Testcontainers(MySQL)** 를 사용해 Docker가 필요한데, GitHub Actions `ubuntu-latest` 러너에 Docker가 내장돼 있어 별도 설정 없이 동작 (로컬 기준 약 40초 소요)
- **required check 이름** : `test`

```yaml
on:
  pull_request:
    branches: [main, develop]
jobs:
  test:
    name: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: gradle
      - run: |
          chmod +x gradlew
          ./gradlew test --no-daemon
```

> `ci.yml`은 `main`·`develop` **양쪽 브랜치에 모두** 존재한다. `pull_request` 워크플로우는 대상(base) 브랜치 기준으로 해석되기 때문에, 두 브랜치 PR을 모두 게이트하려면 양쪽에 있어야 한다.

---

### 📌 브랜치 보호 규칙 (머지 게이트)

`main`, `develop` 두 브랜치에 동일 적용:

| 항목 | 설정 |
| --- | --- |
| Required status check | **`test` 통과해야 머지 가능** |
| Require a pull request before merging | ✅ (직접 push로 우회 불가) |
| 필수 리뷰 승인 수 | 0 (= CI가 게이트, 사람 승인은 강제하지 않음) |
| enforce_admins | off (관리자는 긴급 시 우회 가능, 일반 팀원은 게이트 적용) |

> 즉, 일반 팀원은 **CI(test)가 초록불이어야만** PR을 머지할 수 있다. 테스트가 깨지면 머지 버튼이 비활성화되며, 고쳐서 다시 push하면 CI가 재실행된다.

---

### 📌 개발 흐름 (사용법)

1. 브랜치 생성 → 작업 → `main` 또는 `develop`로 **PR**
2. CI(`test`)가 자동 실행 → **통과해야 머지 가능**
3. `main`에 머지되면 → **test 재확인 후 자동 배포** → 헬스체크까지 검증
4. (수동 배포가 필요하면) GitHub Actions 탭 → "Deploy to EC2" → Run workflow

---

### 📌 구축 중 이슈 & 해결

| 이슈 | 원인 | 해결 |
| --- | --- | --- |
| `compose build requires buildx` | AL2023 도커 패키지에 buildx 미포함 | buildx 플러그인(v0.35.0) 설치 |
| `git clone` 인증 요구 | 저장소가 private | public 전환 (면접 링크용 겸) |
| SSH `ssh: no key found` | 시크릿에 개인키 본문만(BEGIN/END 누락) 붙여넣음 | `pbcopy < gha_deploy`로 **파일 전체** 재등록 |
| 서버 클론이 `develop`에 위치 | clone 시 기본 브랜치가 develop | 배포 스크립트에서 `main` 명시 체크아웃 |

---

### 📌 보안 메모

- 배포용 SSH 개인키는 **GitHub Secret 한 곳에만** 저장(팀원끼리 공유 X). 서버엔 공개키만 등록.
- 개인키는 채팅/커밋에 노출 금지. (`.gitignore`에 `gha_deploy*`, `*.pem` 등 추가해 커밋 차단)
- 노출 시 즉시 폐기·교체: 서버 `authorized_keys`에서 해당 공개키 제거 → 새 키쌍 생성 → 재등록.

---

### 📌 향후 개선

- `compose.yaml`의 DB 비밀번호 평문 → 시크릿/환경변수로 분리 (실서비스 전)
- **진짜 무중단 배포(블루-그린 롤링)** : nginx 등 리버스 프록시 + 앱 컨테이너 2개로 헬스 확인 후 트래픽 전환. 단, 현재 **t2.micro(RAM 1GB)** 로는 앱 2개 동시 기동 시 메모리 초과 위험 → t3.small 이상 또는 도메인/HTTPS 도입 시점에 함께 진행 권장. (현재는 배포 후 헬스체크 검증까지 적용된 상태)
- 도메인 연결(A 레코드 → `3.35.111.143`) + HTTPS(80/443, 인증서)
