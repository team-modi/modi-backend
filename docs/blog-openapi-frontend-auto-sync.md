# 백엔드 API가 바뀌면 프론트가 스스로 고친 PR을 올린다 — OpenAPI 자동 동기화 하네스 구축기

> 대상: 백엔드(Spring Boot + springdoc)와 프론트(손코딩 API 클라이언트)를 **별도 레포**로 운영하는 팀.
> 성격: 실제 구축·검증기 + 따라 하는 가이드. team-modi(백 `modi-backend` / 프론트 `modi-frontend`)에 실제로 적용해 라이브로 검증한 내용을 그대로 정리한다.
> 결론 먼저: 백엔드 `develop`에 API 변경이 머지되면, **사람이 아무것도 안 해도** 프론트 레포에 "`src/api`를 이렇게 바꿔야 한다"는 PR이 자동으로 열린다. 머지는 사람이 리뷰하고 한다.

---

## 1. 왜 필요했나 — 문제

백/프가 레포가 분리돼 있고, 프론트가 API 클라이언트를 **손으로 짜는** 구조(axios 래퍼 등)면 이런 일이 반복된다.

- 백엔드가 엔드포인트 경로/파라미터/응답 필드를 바꾼다.
- 프론트는 그걸 **모른다.** 다음 QA나 런타임 에러로 뒤늦게 발견한다.
- "누가 프론트 `src/api` 좀 맞춰주세요"가 슬랙에 올라온다.

핵심은 **"계약(OpenAPI 스펙)이 바뀐 걸 프론트가 제때 아는 것"** 과 **"그 변경을 코드에 반영하는 지루한 작업"** 두 가지다. 이 둘을 자동화한다.

우리가 세운 원칙:

- **스펙은 1급 아티팩트다.** 백엔드가 `/v3/api-docs`로 뽑은 OpenAPI 스펙을 레포에 커밋해 "현재 계약"으로 박제한다.
- **AI는 좁은 범위만 만진다.** CI 안에서 Claude Code 헤드리스가 **`src/api/`만** 고친다. 컴포넌트/스토어/라우터를 건드리면 **기계가 결정론적으로 되돌린다**(AI에게 복구를 안 맡긴다 — 그러면 경계가 협상 가능해진다).
- **실패는 AI가 먼저 복구, 사람은 마지막.** lint/build 실패는 AI가 자기 세션 안에서 green 될 때까지 고친다(자가복구). 진짜 안 되면 draft PR + `needs-human`으로 사람에게 넘긴다.
- **자동 머지는 없다.** 모든 결과는 PR(성공=PR / 실패=draft). 검증기가 약하므로(lint+build까지만) 머지는 반드시 사람이.

---

## 2. 큰 그림 — 어떻게 도는가

```
[백엔드 레포]                                   [프론트 레포]
develop 머지(src/main 변경)
   │
   ├─ CI: 앱 부팅 → /v3/api-docs 덤프 → 정규화
   ├─ 전용 브랜치 openapi-spec 에 스펙 발행(커밋)
   └─ repository_dispatch 이벤트 발사 ───────────▶ api-sync 워크플로 기동
                                                     │
                                                     ├─ develop 체크아웃, 새 스펙 fetch
                                                     ├─ 커밋된 기준 스펙과 diff (oasdiff)
                                                     ├─ Claude Code가 src/api 수정 (lint/build green까지 자가복구)
                                                     ├─ anti-cheat + 경계밖 기계 되돌리기 + lint/build 게이트
                                                     └─ develop 대상 PR ── green=PR / 실패=draft(needs-human) ── 사람이 리뷰·머지
```

두 레포를 잇는 다리는 **토큰 하나**(`repository_dispatch`를 쏠 수 있는 PAT)다. CI 속 Claude Code는 로컬에서 쓰는 그 CLI와 같은 도구이고, `-p` 헤드리스 모드로 돌 뿐이다.

트리거는 두 가지를 다 지원하게 만든다.

- **옵션 A (즉시):** 백엔드가 머지 즉시 `repository_dispatch`로 프론트를 깨운다. 반응 빠름. 백엔드에 워크플로 1개 추가 필요.
- **옵션 B (폴링):** 프론트가 `schedule`(cron)로 매일 배포된 스웨거를 확인한다. 백엔드 무수정. 반응은 최대 하루.

프론트 워크플로는 두 트리거를 모두 받게 짜두면, B로 시작했다가 나중에 A를 켜도 프론트는 무수정이다.

---

## 3. 사전 준비물

- 백엔드: springdoc(예: `springdoc-openapi-starter-webmvc-ui`)이 이미 붙어 `/v3/api-docs`가 뜨는 상태. (코드 수정 불필요)
- 프론트: API 클라이언트가 한 곳(`src/api/`)에 모여 있고, lint/build 스크립트가 있는 상태.
- Anthropic 인증: **Claude Max/Pro 구독**이면 `claude setup-token`으로 발급한 OAuth 토큰, 또는 크레딧이 있는 **API 키**.
- 두 레포에 대한 관리 권한(시크릿·설정 변경).

---

## 4. 백엔드 쪽 — 스펙을 발행하고 종을 친다

파일 1개만 추가한다: `.github/workflows/publish-openapi.yml`

하는 일:

1. `on.push.branches: [develop]` + `paths: [src/main/**, build.gradle*]` 트리거.
2. MySQL 서비스 컨테이너 띄우고 JDK 셋업.
3. `./gradlew bootRun`을 백그라운드로 띄우고 `curl`로 `/v3/api-docs`가 뜰 때까지 폴링, 성공 시 덤프.
4. `python3 -m json.tool --sort-keys`로 정규화(커밋 diff 안정화).
5. 스펙이 바뀌었으면 **전용 브랜치 `openapi-spec`** 에 커밋·발행.
6. 변경 시 `peter-evans/repository-dispatch`로 프론트 레포에 `openapi-updated` 이벤트 발사.

핵심 스텝(발행+변경감지):

```yaml
- name: Publish spec to openapi-spec branch if changed
  id: commit
  run: |
    mkdir -p openapi
    cp /tmp/openapi.pretty.json openapi/openapi.json
    git add openapi/openapi.json          # ← 반드시 add 후 --cached로 비교 (함정 ① 참고)
    git fetch origin openapi-spec --depth=1 2>/dev/null || true
    if git cat-file -e origin/openapi-spec:openapi/openapi.json 2>/dev/null; then
      git show origin/openapi-spec:openapi/openapi.json > /tmp/prev.json
      if diff -q /tmp/prev.json openapi/openapi.json >/dev/null; then
        echo "changed=false" >> "$GITHUB_OUTPUT"; exit 0
      fi
    fi
    git config user.name  "api-sync-bot"
    git config user.email "api-sync-bot@users.noreply.github.com"
    git checkout -B openapi-spec           # ← develop에 직접 push 금지 (함정 ② 참고)
    git commit -m "chore: publish openapi spec [skip ci]"
    git push -f origin openapi-spec
    echo "changed=true" >> "$GITHUB_OUTPUT"

- name: Dispatch to frontend
  if: steps.commit.outputs.changed == 'true'
  uses: peter-evans/repository-dispatch@v3
  with:
    token: ${{ secrets.FE_REPO_PAT }}
    repository: <org>/<frontend-repo>
    event-type: openapi-updated
    client-payload: >-
      { "spec_url": "https://raw.githubusercontent.com/<org>/<backend-repo>/openapi-spec/openapi/openapi.json" }
```

부팅에 필요한 env(DB 접속 등)는 스텝 env로 주입한다. 우리 경우 JWT/OAuth 시크릿은 `application.yaml`에 로컬 기본값이 있어 **스펙 덤프용 부팅엔 추가 시크릿이 필요 없었다.** 여러분 앱은 부팅 필수 env가 더 있는지 확인하고 `${{ secrets.XXX }}`로 채워라.

---

## 5. 프론트 쪽 — 스펙을 받아 AI가 고치고 PR을 연다

파일 3개:

1. `.github/workflows/api-sync.yml` — 오케스트레이터
2. `api-sync/prompts/sync.md` — CI 속 Claude Code에게 줄 작업 지시서
3. `api-sync/spec/openapi.json` — 기준 스펙(부트스트랩 1회 커밋; 없으면 최초 실행이 자동 seeding)

`api-sync.yml`의 스텝 순서:

1. **checkout — `ref: develop` 명시** (dispatch/schedule는 기본 브랜치를 체크아웃하므로 안 쓰면 main 기준으로 고치는 버그가 된다).
2. `setup-node` + `npm ci`.
3. 스펙 fetch → 정규화 → 기준 스펙과 `diff`. 같으면 이후 전부 skip.
4. `oasdiff`로 changelog + breaking 여부 분류.
5. **AI 동기화(자가복구 포함)**: `npm i -g @anthropic-ai/claude-code` 후
   ```bash
   claude -p "$(cat api-sync/prompts/sync.md)" \
     --allowedTools "Read,Grep,Glob,Edit,Bash(npm run lint*),Bash(npm run build*)" \
     --max-turns 50
   ```
   env에 `CLAUDE_CODE_OAUTH_TOKEN`(구독) 또는 `ANTHROPIC_API_KEY`(API). AI는 자기 턴 안에서 lint/build를 직접 돌려 **green이 될 때까지 `src/api`를 고친다**(인세션 자가복구). `--max-turns`가 그 상한(유계).
6. **anti-cheat(치팅 차단)**: `git diff -- src/api`에 `eslint-disable`·`@ts-ignore`가 새로 붙었으면 `::error::`로 폐기(규칙 끄고 통과 금지).
7. **가드레일 = 결정론적 되돌리기**: `^(src/api/|api-sync/)` 밖 변경은 실패시키지 않고 **기계가 되돌린다**(편집=`git checkout`, 새 파일=`git clean`). 침범한 AI에게 복구를 맡기면 경계가 협상 가능해지므로.
8. **검증 게이트**: `npm run lint && npm run build`를 재실행해 결과만 기록(여기서 죽지 않음). **green** → PR로. **실패**(되돌린 뒤에도 안 됨=흡수 불가) → 사람 에스컬레이션.
9. 새 스펙을 기준 스펙 위치로 승격(promote).
10. `peter-evans/create-pull-request`로 **base: develop** PR. 브랜치는 고정 이름 `api-sync/pending`(재실행이 중복 PR 안 쌓고 하나를 갱신). **green이면 일반 PR**(자가복구 흔적 있으면 `auto-recovered` 라벨), **실패면 draft PR + `needs-human` 라벨 + sync-notes**로 — 성공이든 실패든 **항상 사람이 보는 산출물**로 끝난다.

`sync.md`(AI 규칙) 요지:

- 읽는 순서: changelog → 새 스펙 전문 → Grep으로 `src/api` 영향 지점.
- **수정 허용은 `src/api/**` 뿐.** 리팩토링 금지, changelog에 근거 없는 수정 금지.
- **당신 레포의 실제 컨벤션을 명시하라.** (우리는 `@utils/axiosInstance` default import, named `export const` async 함수, PUT-not-PATCH, baseURL 상대경로를 예시로 박아뒀다. 이걸 안 적으면 AI가 자기 스타일로 짠다.)
- 흡수 불가능한 변경(화면 필수 데이터가 스펙에서 삭제 등)은 고치지 말고 `sync-notes.md`에 기록 후 중단 — 사람이 결정할 문제.
- **완료 조건 = green으로 끝내라**: lint/build가 통과할 때까지 src/api를 고쳐 반복(자가복구). 단 `eslint-disable`/`@ts-ignore`로 "통과시키지" 말 것(치팅 차단 가드레일이 폐기). 여러 번 시도해도 안 되면 멈추고 sync-notes에 원인.

---

## 6. 시크릿 & 설정 (이걸 안 하면 안 돈다)

| 위치 | 항목 | 용도 |
|---|---|---|
| 프론트 > Secrets | `CLAUDE_CODE_OAUTH_TOKEN` (또는 `ANTHROPIC_API_KEY`) | CI 속 Claude Code 인증 |
| 백엔드 > Secrets | `FE_REPO_PAT` | 프론트로 dispatch 쏠 토큰 (아래 함정 ③) |
| 프론트 > Variables | `BACKEND_SPEC_URL` | 옵션 B 폴링 시 스펙 주소 |
| **조직 설정** | "Allow GitHub Actions to create and approve pull requests" **ON** | create-pull-request가 이거 없으면 막힘(함정 ⑤) |

OAuth 토큰은 로컬에서 `claude setup-token`으로 발급한다. **일반 터미널에서** 실행해야 토큰(`sk-ant-oat...`)이 화면에 찍힌다(파이프/백그라운드로 돌리면 출력이 안 잡힌다). 그 값을 프론트 시크릿에 넣는다.

---

## 7. 실전에서 밟은 함정 5개 — 여기가 진짜 알맹이

문서만 보고 짜면 다 초록일 것 같지만, **실제로 돌려보면** 아래가 하나씩 터진다. 우리가 라이브 E2E로 잡은 것들이다.

### 함정 ① 최초 발행이 통째로 누락된다 (untracked 파일)
- 증상: 첫 실행에서 스펙은 덤프됐는데 커밋·dispatch가 조용히 skip.
- 원인: `git diff --quiet openapi.json`은 **untracked(신규) 파일을 못 본다** → "변경 없음"으로 오판.
- 해결: `git add` 후 **`git diff --cached --quiet`** 로 스테이지 기준 비교.

### 함정 ② 워크플로가 보호 브랜치에 push하다 거부된다 (GH006)
- 증상: `remote: error: GH006: Protected branch update failed for refs/heads/develop`.
- 원인: `develop`이 "PR 필수" 보호 브랜치라 워크플로의 직접 push가 막힘.
- 해결: 스펙을 **사람이 안 만지는 전용 비보호 브랜치 `openapi-spec`** 에 발행하고, `spec_url`을 그쪽으로. 보호 정책을 건드리지 않는다.

### 함정 ③ dispatch 토큰이 403 — "개인 소유" fine-grained PAT의 함정
- 증상: dispatch 스텝이 `Resource not accessible by personal access token` (403).
- 원인: fine-grained PAT는 **읽기는 public 레포 아무거나 되지만, 쓰기는 "토큰의 Resource owner가 소유한 레포"에만** 된다. 조직 레포인데 토큰 소유자가 개인계정이면 write(=repository_dispatch)가 안 된다. Contents: Read and write를 켜도 소용없다.
- 해결(둘 중 하나):
  - **클래식 PAT + `repo` 스코프** (가장 확실. 해당 레포에 write 권한이 있는 계정이면 됨).
  - 또는 fine-grained PAT를 만들 때 **Resource owner를 조직으로** 선택 → 조직 오너 승인.

### 함정 ④ 프론트 워크플로가 아예 안 깨어난다 — 기본 브랜치 규칙
- 증상: dispatch는 성공(백엔드 초록)인데 프론트 워크플로가 안 돎. `workflow_dispatch`도 "not found on the default branch".
- 원인: GitHub은 **`repository_dispatch`·`schedule`·`workflow_dispatch`를 "기본 브랜치에 있는 워크플로"만 실행**한다. 프론트 기본 브랜치가 `main`인데 워크플로를 `develop`에만 뒀다면 영원히 안 깨어난다.
- 해결: `api-sync.yml`을 **기본 브랜치(main)에** 둔다. 잡은 여전히 `ref: develop`로 체크아웃하고 develop으로 PR을 연다. (백엔드는 기본 브랜치가 develop이라 push 트리거는 문제 없었다 — push는 해당 브랜치에서 도니까.)

### 함정 ⑤ create-pull-request가 막힌다 — 조직 정책
- 증상: 마지막 "Create PR" 스텝만 실패.
- 원인: "Allow GitHub Actions to create and approve pull requests"가 조직/레포에서 기본 OFF.
- 해결: 조직 설정(또는 레포 설정)에서 ON. 저위험(PR 생성만 허용, 머지는 여전히 사람).

> 교훈: **문서 검증(yamllint)과 실제 CI 실행은 다르다.** 위 5개는 전부 실행해봐야만 드러났다. 새 자동화는 반드시 라이브로 한 바퀴 돌려서 확인하라.

---

## 8. 첫 동작 확인 & 운영

부트스트랩(기준 스펙 1회):
```bash
curl -s "$BACKEND_SPEC_URL" | python3 -m json.tool --sort-keys > api-sync/spec/openapi.json
git add . && git commit -m "chore: bootstrap api-sync baseline"
```
옵션 A면 이 단계도 건너뛸 수 있다 — 첫 실행이 "initial"로 처리되며 baseline을 알아서 심는다.

E2E 확인:
1. 백엔드 `develop`에 API 변경(예: 새 엔드포인트) 머지.
2. 백엔드 `publish-openapi` 초록 → Dispatch 스텝까지 success 확인.
3. 프론트 Actions에서 `api-sync` 런이 **`event=repository_dispatch`** 로 자동 기동됐는지 확인.
4. 열린 PR에서 `src/api` diff 확인 → 리뷰 → 머지.

일상: 백엔드 API가 바뀌어 develop에 머지될 때마다, 프론트에 "이렇게 맞춰야 함" PR이 자동으로 뜬다. 리뷰어는 diff + PR 본문의 changelog/sync-notes를 보고 **브라우저에서 실제 화면 동작만 확인**하면 된다(검증이 lint+build까지라 의미 검증은 사람 몫).

---

## 9. 보안 주의

- 토큰(PAT·OAuth·API 키)을 **채팅/이슈/커밋에 붙여넣지 마라.** 노출되면 즉시 폐기·재발급. 시크릿은 반드시 GitHub Secrets에만.
- `FE_REPO_PAT`는 필요한 최소 권한만. 클래식이면 `repo` 하나로 충분하다.
- CI 속 Claude Code는 `--allowedTools`로 능력을 제한(읽기+편집+lint/build만) + **anti-cheat(억제주석 차단)** + **경계 위반은 기계가 되돌림**(협상 불가) + PR(자동 머지 없음) = 다중 방어. 핵심은 **비대칭** — lint 실패는 AI가 고치되, 경계 침범은 기계가 되돌린다.

---

## 10. 한계와 확장

지금 구조는 "쉽고 빠르게"에 최적화한 lean 버전이다. 검증기가 lint+build라 **의미상 올바른지는 보장 못 한다** — 그래서 사람 리뷰가 필수다.

이미 얹은 것(초기 lean → 현재):
- ✅ **인세션 자가복구**: lint/build 실패를 AI가 자기 세션 안에서 green 될 때까지 고침(`--max-turns`가 상한). 실패해도 조용히 죽지 않고 draft PR + `needs-human`으로 사람에게.
- ✅ **결정론적 가드레일 + anti-cheat**: 경계 밖 변경은 기계가 되돌리고, 억제주석 치팅은 폐기.
- ✅ **중복 방지**: 고정 브랜치(`api-sync/pending`)로 재실행이 PR을 갱신.

아직 남은 확장:
- **Playwright 게이트**: 새 스펙으로 목 서버(Prism 등)를 띄우고 기존 E2E를 돌려 "AI 수정이 새 계약에서 실제로 동작하는가"까지 판정(현재는 lint+build까지).
- **non-breaking 자동 머지**: 검증기가 충분히 강해진 뒤에만.

---

## 부록: 파일 위치 요약

```
backend-repo/
└─ .github/workflows/publish-openapi.yml     # 스펙 발행 + dispatch
   (+ 런타임: openapi-spec 브랜치에 openapi/openapi.json 발행)

frontend-repo/
├─ .github/workflows/api-sync.yml            # 기본 브랜치(main)에! 잡은 develop 체크아웃·PR
└─ api-sync/
   ├─ prompts/sync.md                        # AI 작업 지시서(레포 컨벤션 명시)
   └─ spec/openapi.json                      # 기준 스펙(develop)
```

실제 구현은 team-modi(`modi-backend` / `modi-frontend`)에 적용해 옵션 A(dispatch)·B(폴링) 둘 다 라이브로 검증했다.
