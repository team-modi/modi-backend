# `api-sync.yml` 한 줄씩 뜯어보기 — 초보자용 완전 해설

> 대상: GitHub Actions·셸(bash)·YAML이 아직 낯선 초보 개발자.
> 다루는 파일: `modi-frontend/.github/workflows/api-sync.yml` (백엔드 API가 바뀌면 프론트 `src/api`를 AI가 고친 PR을 여는 워크플로).
> 방식: 위에서부터 **항목·명령어를 하나씩** 설명한다. 몰라도 되는 건 없다.

---

## 0. 먼저, GitHub Actions가 뭔지 (30초)

- **GitHub Actions** = GitHub이 제공하는 "자동 실행 로봇". 레포에 `.github/workflows/*.yml` 파일을 두면, 어떤 사건(push, 예약 시각, 버튼 클릭 등)이 생길 때 GitHub이 **깨끗한 리눅스 컴퓨터를 하나 빌려서** 그 YAML에 적힌 명령을 순서대로 실행한다.
- 이 YAML 한 개를 **워크플로(workflow)**, 그 안의 작업 묶음을 **잡(job)**, 잡 안의 한 단계를 **스텝(step)** 이라 부른다.
- **YAML 문법**: `키: 값` 형태. 들여쓰기(공백)로 계층을 표현한다. `#`으로 시작하면 주석. `- ` 로 시작하면 목록(리스트) 항목.

이 파일의 구조를 크게 보면:
```
name / on / concurrency / permissions / env   ← 설정부 (언제·어떤 권한으로 도나)
jobs:
  sync:
    steps: [ ①~⑦ ]                            ← 실제로 하는 일 (스텝들)
```

---

## 1. 헤더 — 이름과 주석

```yaml
name: api-sync
```
- `name`: Actions 탭에 표시될 워크플로 이름. 그냥 라벨이다.
- 그 아래 `#` 줄들은 전부 **주석**(사람용 메모). 실행에 영향 없음.

---

## 2. `on:` — 언제 실행되나 (트리거 3종)

```yaml
on:
  repository_dispatch:
    types: [openapi-updated]
  schedule:
    - cron: '0 0 * * *'        # 매일 00:00 UTC = 09:00 KST
  workflow_dispatch:
```
`on`은 "이 워크플로를 **언제** 깨울까"를 정한다. 여기선 3가지:

- **`repository_dispatch` (옵션 A, 즉시)**: 다른 곳에서 "이벤트 신호"를 쏘면 실행. `types: [openapi-updated]`는 "이름이 `openapi-updated`인 신호만 받겠다"는 뜻. 백엔드 CI가 스펙을 바꾸면 이 신호를 쏜다.
- **`schedule` (옵션 B, 예약)**: 정해진 시각마다 자동 실행. `cron: '0 0 * * *'`은 **크론 표현식**이다:
  | 필드 | `0` | `0` | `*` | `*` | `*` |
  |---|---|---|---|---|---|
  | 의미 | 분(0분) | 시(0시) | 일(매일) | 월(매월) | 요일(매요일) |
  → "매일 0시 0분(UTC)"에 실행. UTC 0시 = 한국 9시.
- **`workflow_dispatch`**: GitHub Actions 탭에서 **"Run workflow" 버튼**으로 수동 실행할 수 있게 함.

> 왜 3개나? A(즉시)로 빠르게, B(예약)로 신호를 놓쳐도 하루 안에 따라잡고, 수동으로 언제든 돌려볼 수 있게 — 3중 안전망.

---

## 3. `concurrency:` — 동시에 여러 개 못 돌게

```yaml
concurrency:
  group: api-sync
  cancel-in-progress: false
```
- 같은 `group` 이름("api-sync")을 가진 실행은 **한 번에 하나만** 돌게 한다(겹쳐 돌면서 PR을 꼬이게 만드는 걸 방지).
- `cancel-in-progress: false`: 새 실행이 왔다고 **돌던 걸 취소하지 않는다**(끝날 때까지 기다렸다가 다음 걸 돌림).

---

## 4. `permissions:` — 이 로봇이 가질 권한

```yaml
permissions:
  contents: write
  pull-requests: write
```
- Actions가 쓰는 자동 토큰(`GITHUB_TOKEN`)의 권한을 정한다.
- `contents: write` = 파일·브랜치 쓰기(커밋/푸시), `pull-requests: write` = PR 만들기. 이게 있어야 마지막에 PR을 열 수 있다.

---

## 5. `env:` — 스텝들이 공통으로 쓰는 변수

```yaml
env:
  SPEC_URL: ${{ github.event.client_payload.spec_url || vars.BACKEND_SPEC_URL }}
  OLD_SPEC: api-sync/spec/openapi.json
  NEW_SPEC: api-sync/out/openapi.new.json
```
- `env`에 정의한 값은 아래 모든 스텝에서 `$이름`으로 쓸 수 있다(환경 변수).
- `${{ ... }}`는 **GitHub 표현식**(실행 시점에 값으로 치환됨).
  - `github.event.client_payload.spec_url`: A(dispatch)로 왔을 때 신호에 실려온 스펙 주소.
  - `|| vars.BACKEND_SPEC_URL`: 그게 없으면(B/수동) 레포에 저장해 둔 변수 사용. `||`는 "앞이 비면 뒤를 써라".
- `OLD_SPEC` = **프론트가 아는 현재 계약**(레포에 커밋된 기준 스펙). `NEW_SPEC` = **방금 받아온 새 스펙**. 이 둘을 비교하는 게 핵심.

---

## 6. `jobs:` / `steps:` 읽는 법 (공통 개념)

```yaml
jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - uses: ...        # ← 남이 만든 액션을 가져다 씀
      - name: ...        # ← 이 스텝의 이름
        run: ...         # ← 셸(bash) 명령을 직접 실행
```
- `runs-on: ubuntu-latest`: "우분투 리눅스 최신 버전 컴퓨터에서 돌려라".
- 스텝은 두 종류:
  - **`uses:`** — 남이 만든 재사용 액션을 불러 씀(예: 체크아웃, 노드 설치).
  - **`run:`** — 셸 명령을 직접 실행. `run: |` 의 `|`는 "여러 줄을 그대로"라는 뜻.
- 스텝에 붙는 것들:
  - `id:` — 이 스텝에 별명. 나중에 `steps.<id>.outputs.<키>`로 결과를 참조.
  - `if:` — 조건. 참일 때만 이 스텝 실행. (예: `if: steps.change.outputs.changed == 'true'` = "변경이 있을 때만")
  - `$GITHUB_OUTPUT` — 스텝이 "결과 값"을 다음 스텝에 넘기는 특수 파일. `echo "키=값" >> "$GITHUB_OUTPUT"`로 기록한다.

---

## 7. 준비 스텝 — 코드 내려받고 도구 설치

```yaml
      - uses: actions/checkout@v4
        with:
          ref: develop
```
- `checkout`: 레포 코드를 이 컴퓨터로 내려받는다. `ref: develop`은 **develop 브랜치**를 받으라는 것.
- ⚠️ 이게 중요: dispatch/예약 실행은 기본 브랜치(main)를 받는데, 우리는 develop을 고쳐야 하므로 **명시적으로 develop을 지정**. 이걸 빼면 엉뚱한 브랜치를 고치는 버그가 된다.

```yaml
      - uses: actions/setup-node@v4
        with:
          node-version: 22.13.0
          cache: npm
      - run: npm ci
```
- `setup-node`: Node.js 22.13.0 설치. `cache: npm`은 의존성 캐시로 속도 향상.
- `npm ci`: `package-lock.json` 기준으로 의존성(node_modules)을 **정확히 재설치**. (`npm install`보다 CI에 적합 — 잠금파일 그대로 설치.)

---

## 8. 스텝 ① 변경 감지 — "스펙이 진짜 바뀌었나?"

```yaml
      - name: Fetch spec & detect change
        id: change
        run: |
          mkdir -p api-sync/out api-sync/spec
          curl -sSfL "$SPEC_URL" | python3 -m json.tool --sort-keys > "$NEW_SPEC"
          if [ -f "$OLD_SPEC" ] && diff -q "$OLD_SPEC" "$NEW_SPEC" > /dev/null; then
            echo "changed=false" >> "$GITHUB_OUTPUT"
            echo "스펙 변경 없음 - 종료"
          else
            echo "changed=true" >> "$GITHUB_OUTPUT"
          fi
```
한 줄씩:
- `mkdir -p api-sync/out api-sync/spec`: 결과 저장할 폴더 생성. `-p`는 "이미 있으면 에러 내지 말고, 중간 경로도 알아서 만들어라".
- `curl -sSfL "$SPEC_URL"`: 스펙 URL에서 내용을 **다운로드**. 플래그 뜻:
  | 플래그 | 뜻 |
  |---|---|
  | `-s` | 진행바 안 보이게(조용히) |
  | `-S` | 근데 에러는 보여줘 |
  | `-f` | 서버가 404 등 실패하면 **실패로 처리**(조용히 빈 결과 만들지 말고) |
  | `-L` | 리다이렉트(주소 이동) 따라가라 |
- `| python3 -m json.tool --sort-keys`: 받은 JSON을 **정규화**(키 정렬+포맷). `|`(파이프)는 "앞 명령의 출력을 뒤 명령의 입력으로". → 왜 정규화하냐는 별도 문서(`blog-openapi-spec-normalization.md`) 참고. 요약: "내용 같으면 글자도 같게" 만들어야 diff가 정확.
- `> "$NEW_SPEC"`: 결과를 새 스펙 파일로 저장.
- `if [ -f "$OLD_SPEC" ] && diff -q ... > /dev/null`:
  - `[ -f "$OLD_SPEC" ]`: 기준 스펙 파일이 **존재하면**(`-f` = 파일 있냐).
  - `&&`: 그리고
  - `diff -q A B`: 두 파일 비교. `-q`는 "다른지/같은지만 조용히"(내용 출력 X). `> /dev/null`은 출력 버림.
  - `diff`는 **같으면 성공(0), 다르면 실패(비0)** 를 반환 → `if`가 "존재 && 동일"이면 참.
- 같으면 `changed=false`(할 일 없음), 다르면 `changed=true`. 이 값을 `$GITHUB_OUTPUT`에 기록 → 아래 스텝들이 `if`로 참조.

> 이후 모든 스텝엔 `if: steps.change.outputs.changed == 'true'`가 붙어 있다 = **변경이 있을 때만 실행**. 안 바뀌었으면 여기서 조용히 끝(헛 PR 없음).

---

## 9. 스텝 ② 무엇이·어떻게 바뀌었나 (oasdiff)

```yaml
      - name: Diff changelog (oasdiff)
        if: steps.change.outputs.changed == 'true'
        id: classify
        run: |
          if [ -f "$OLD_SPEC" ]; then
            docker run --rm -v "$PWD:/w" tufin/oasdiff changelog "/w/$OLD_SPEC" "/w/$NEW_SPEC" \
              > api-sync/out/changelog.txt || true
            if docker run --rm -v "$PWD:/w" tufin/oasdiff breaking "/w/$OLD_SPEC" "/w/$NEW_SPEC" --fail-on ERR > /dev/null 2>&1; then
              echo "classification=non-breaking" >> "$GITHUB_OUTPUT"
            else
              echo "classification=breaking" >> "$GITHUB_OUTPUT"
            fi
          else
            echo "(최초 실행 - 기준 스펙 없음)" > api-sync/out/changelog.txt
            echo "classification=initial" >> "$GITHUB_OUTPUT"
          fi
```
- `oasdiff`는 OpenAPI 스펙 두 개를 비교해주는 **외부 오픈소스 도구**(우리가 만든 게 아님). 여기선 도커 이미지로 불러 쓴다.
- `docker run --rm -v "$PWD:/w" tufin/oasdiff <명령> <옛> <새>`:
  | 조각 | 뜻 |
  |---|---|
  | `docker run` | 도커 컨테이너 실행 |
  | `--rm` | 끝나면 컨테이너 삭제(청소) |
  | `-v "$PWD:/w"` | 현재 폴더(`$PWD`)를 컨테이너 안 `/w`로 **연결(mount)** → 컨테이너가 우리 파일을 볼 수 있게 |
  | `tufin/oasdiff` | 쓸 도커 이미지 이름 |
- `changelog`: "무엇이 바뀌었는지" 사람이 읽을 목록 → `changelog.txt`로 저장. `|| true`는 "실패해도 워크플로 죽이지 마"(변경목록은 부가정보라).
- `breaking ... --fail-on ERR`: "하위호환 깨는(breaking) 변경이 있으면 **실패(비0) 반환**". 그 성공/실패를 `if`로 받아 → 통과면 `non-breaking`, 아니면 `breaking`으로 분류. `> /dev/null 2>&1`은 화면 출력·에러 다 버림(우린 성공/실패 코드만 필요).
- 기준 스펙이 아예 없으면(최초) `initial`로 분류.
- 이 `classification` 값은 나중에 **PR 라벨**로 쓰인다.

---

## 10. 스텝 ③ AI 동기화 (자가복구 포함)

```yaml
      - name: AI sync (Claude Code headless, self-healing)
        if: steps.change.outputs.changed == 'true'
        env:
          CLAUDE_CODE_OAUTH_TOKEN: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}
        run: |
          npm install -g @anthropic-ai/claude-code
          claude -p "$(cat api-sync/prompts/sync.md)" \
            --allowedTools "Read,Grep,Glob,Edit,Bash(npm run lint*),Bash(npm run build*)" \
            --max-turns 50 \
            || echo "agent ended non-zero (검증 단계에서 판정)"
```
- `env: CLAUDE_CODE_OAUTH_TOKEN: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}`: 레포 **비밀값(Secret)** 을 이 스텝의 환경변수로 주입. Claude Code가 이걸로 로그인(네 Max 구독).
- `npm install -g @anthropic-ai/claude-code`: 로컬에서 쓰는 그 Claude Code CLI를 이 컴퓨터에 설치(`-g`=전역).
- `claude -p "..."`: **비대화형(headless) 실행**. `-p`(=print/prompt)는 "이 프롬프트로 한 번 돌고 결과를 내라"(사람이 채팅 안 함).
  - `"$(cat api-sync/prompts/sync.md)"`: `cat`으로 지시서 파일 내용을 읽어 그대로 프롬프트로 넣음. `$(...)`는 "명령 결과를 그 자리에 끼워넣기".
  - `--allowedTools "..."`: **AI가 쓸 수 있는 도구를 제한**. 읽기(Read/Grep/Glob)·파일편집(Edit)·그리고 `npm run lint`/`npm run build` 실행만 허용. 임의 명령·네트워크·git push는 못 함.
  - `--max-turns 50`: AI가 최대 50번까지 생각·행동 반복 가능(**상한**). 이 안에서 스스로 lint/build 돌려 green 될 때까지 고친다(=인세션 **자가복구**).
  - `|| echo "..."`: AI가 실패로 끝나도(비0) **여기서 워크플로를 죽이지 않는다**. 판정은 아래 검증 스텝이 함.

---

## 11. 스텝 ③.5 치팅 차단 (anti-cheat)

```yaml
      - name: Anti-cheat (no new suppression comments)
        if: steps.change.outputs.changed == 'true'
        run: |
          if git diff -- src/api | grep -E '^\+' | grep -Eq 'eslint-disable|@ts-ignore|@ts-nocheck'; then
            echo "::error::억제 주석 ... 통과 시도 감지 — 폐기"
            git diff -- src/api | grep -E '^\+' | grep -E 'eslint-disable|@ts-ignore|@ts-nocheck'
            exit 1
          fi
```
- AI가 lint를 "규칙을 꺼서" 통과하려는 꼼수(`eslint-disable` 등 주석 추가)를 **기계로 막는다.**
- `git diff -- src/api`: src/api에서 바뀐 내용 출력. `| grep -E '^\+'`: **추가된 줄**(맨 앞이 `+`)만 추림(`-E`=확장 정규식). `| grep -Eq '...'`: 그 중 억제 주석 패턴이 있으면(`-q`=조용히, 있으면 성공).
- 있으면 → `::error::`(Actions에 빨간 에러 표시) 찍고 `exit 1`(**이 스텝 실패 → 워크플로 중단**). 꼼수 코드는 절대 PR로 안 나감.

---

## 12. 스텝 ④ 가드레일 — 경계 밖은 "AI가 아니라 기계가 되돌린다"

```yaml
      - name: Guardrail — deterministic revert of out-of-boundary changes
        id: guardrail
        if: steps.change.outputs.changed == 'true'
        run: |
          bad=$(git diff --name-only | grep -Ev '^(src/api/|api-sync/)' || true)
          new=$(git ls-files --others --exclude-standard | grep -Ev '^(src/api/|api-sync/)' || true)
          if [ -n "$bad" ]; then
            echo "::warning::경계 밖 편집 되돌리기:"; echo "$bad"
            printf '%s\n' "$bad" | while IFS= read -r f; do [ -n "$f" ] && git checkout -- "$f"; done
          fi
          if [ -n "$new" ]; then
            echo "::warning::경계 밖 새 파일 삭제:"; echo "$new"
            printf '%s\n' "$new" | while IFS= read -r f; do [ -n "$f" ] && git clean -f -- "$f"; done
          fi
          reverted=$(printf '%s\n%s\n' "$bad" "$new" | grep -v '^$' || true)
          { echo "reverted<<EOF"; echo "$reverted"; echo "EOF"; } >> "$GITHUB_OUTPUT"
```
AI는 `src/api`만 고쳐야 하는데 혹시 밖을 건드렸으면, **그 부분만 되돌린다**(AI에게 "네가 고쳐"라고 안 시킴 — 경계는 협상 불가).
- `bad=$(...)`: `$(...)` 결과를 변수 `bad`에 저장.
  - `git diff --name-only`: 바뀐 **파일 이름 목록**.
  - `| grep -Ev '^(src/api/|api-sync/)'`: `-v`=**제외**(매칭 안 되는 줄만). 즉 `src/api/`·`api-sync/`로 **시작하지 않는** 파일 = **경계 밖 편집**.
  - `|| true`: grep이 아무것도 못 찾아 실패해도 스텝 안 죽게.
- `new=$(git ls-files --others --exclude-standard | grep -Ev ...)`: `ls-files --others --exclude-standard`는 **아직 git에 없는 새 파일**(.gitignore 제외) 목록 → 경계 밖 새 파일.
- `if [ -n "$bad" ]`: `-n`은 "문자열이 비어있지 않으면"(=경계 밖 편집이 있으면).
- `printf '%s\n' "$bad" | while IFS= read -r f; do ... git checkout -- "$f"; done`: 목록을 **한 줄씩 읽어** 각 파일을 `git checkout -- 파일`로 **원상복구**(마지막 커밋 상태로 되돌림). `while read`로 도는 이유는 파일이 여러 개거나 이름에 공백이 있어도 안전하게 처리하려고.
- 새 파일은 `git clean -f -- "$f"`로 **삭제**(`-f`=강제).
- 마지막: 되돌린 목록을 `reverted`로 모아 `$GITHUB_OUTPUT`에 기록(PR 본문에 "무엇을 되돌렸는지" 남기려고). `<<EOF ... EOF`는 **여러 줄 값**을 출력에 담는 문법(heredoc).

---

## 13. 스텝 ⑤ 검증 게이트 — lint + build (여기가 최종 판정)

```yaml
      - name: Verify (lint + build)
        id: verify
        if: steps.change.outputs.changed == 'true'
        run: |
          if npm run lint && npm run build; then
            echo "green=true" >> "$GITHUB_OUTPUT"
          else
            echo "green=false" >> "$GITHUB_OUTPUT"
            echo "::warning::lint+build 실패 ... 사람 에스컬레이션"
          fi
```
- 경계 밖을 되돌린 **뒤에** lint+build를 **다시** 돌린다.
- `if npm run lint && npm run build`: 둘 다 성공하면(`&&`) 참.
  - **통과** → `green=true` (PR로).
  - **실패** → `green=false` (되돌린 뒤에도 안 됨 = src/api만으론 흡수 불가 → 사람에게).
- 핵심: 이 스텝은 실패해도 `exit 1` 안 함 → **죽지 않고 결과(green)만 기록**. 그래야 아래에서 "실패면 draft PR" 분기를 태울 수 있다.

---

## 14. 스텝 ⑥ 스펙 승격 + PR 본문·라벨·draft 결정

```yaml
      - name: Promote spec, build PR body & decide labels
        id: prep
        env:
          GREEN: ${{ steps.verify.outputs.green }}
          REVERTED: ${{ steps.guardrail.outputs.reverted }}
          CLASSIFICATION: ${{ steps.classify.outputs.classification }}
        run: |
          cp "$NEW_SPEC" "$OLD_SPEC"
          recovered=false
          [ -n "$(printf '%s' "$REVERTED" | tr -d '[:space:]')" ] && recovered=true
          grep -q 'auto-recovered' api-sync/out/sync-notes.md 2>/dev/null && recovered=true
          if [ "$GREEN" = "true" ]; then
            echo "draft=false" >> "$GITHUB_OUTPUT"
            ... labels=api-sync,$CLASSIFICATION[,auto-recovered]
          else
            echo "draft=true" >> "$GITHUB_OUTPUT"
            ... labels=api-sync,$CLASSIFICATION,needs-human
          fi
          { echo "## API sync ..."; ...; } > api-sync/out/pr-body.md
```
- `env:` 로 앞 스텝들의 결과(green/reverted/classification)를 **환경변수로 안전하게** 가져온다(표현식을 셸에 직접 끼우면 위험하니 env로 받는 게 정석).
- `cp "$NEW_SPEC" "$OLD_SPEC"`: 새 스펙을 **기준 스펙 위치로 복사**(승격). 이 PR이 머지되면 "프론트가 아는 계약"이 갱신됨.
- `recovered` 계산:
  - `[ -n "$(... tr -d '[:space:]')" ]`: `tr -d '[:space:]'`는 공백 제거. 되돌린 게 하나라도 있으면 참 → `recovered=true`.
  - `grep -q 'auto-recovered' sync-notes.md`: AI가 자가복구 로그를 남겼으면 참.
- `if [ "$GREEN" = "true" ]`:
  - **green** → `draft=false`(정상 PR), 라벨 `api-sync,<breaking여부>` (+되돌림/복구 있었으면 `auto-recovered`).
  - **실패** → `draft=true`(초안 PR), 라벨에 `needs-human`, 제목에 "FAILED".
- `{ echo ...; ... } > pr-body.md`: 여러 줄을 묶어 PR 본문 파일로 저장 — changelog, AI 노트(sync-notes), 되돌린 목록, 검증 결과, "동작 확인은 리뷰어 몫" 경고까지. **성공이든 실패든 사람이 볼 산출물을 만든다**(조용한 죽음 방지).

---

## 15. 스텝 ⑦ PR 생성

```yaml
      - name: Create PR
        if: steps.change.outputs.changed == 'true'
        uses: peter-evans/create-pull-request@v6
        with:
          base: develop
          branch: api-sync/pending
          draft: ${{ steps.prep.outputs.draft }}
          commit-message: 'chore(api): sync with backend spec'
          title: ${{ steps.prep.outputs.title }}
          body-path: api-sync/out/pr-body.md
          labels: ${{ steps.prep.outputs.labels }}
          add-paths: |
            api-sync/spec/**
            src/api/**
```
- `peter-evans/create-pull-request@v6`: 바뀐 파일을 커밋하고 **PR을 만들어주는** 유명한 액션. `with:`는 그 액션에 주는 설정값들:
  | 항목 | 뜻 |
  |---|---|
  | `base: develop` | 이 PR을 **develop에 병합**하려는 것 |
  | `branch: api-sync/pending` | PR용 **고정 브랜치 이름**. 재실행해도 새 PR 안 만들고 이 브랜치의 기존 PR을 **갱신**(중복 방지) |
  | `draft` | 앞에서 정한 값. 실패면 `true`(초안 PR) |
  | `commit-message` | 커밋 메시지 |
  | `title` / `body-path` | PR 제목 / 본문(파일에서 읽음) |
  | `labels` | 붙일 라벨(콤마로 여러 개) |
  | `add-paths` | **이 경로의 변경만** 커밋에 담음 → `api-sync/spec/**`(갱신된 스펙)와 `src/api/**`(AI 수정본)만. 나머진 제외 |
- 여기서 워크플로 끝. **자동 머지는 없다** — 사람이 PR을 보고 머지한다.

---

## 16. 전체 흐름 한 줄 요약

```
스펙 받기(정규화) → 바뀌었나?(diff)
  └─ 안 바뀜 → 끝
  └─ 바뀜 → oasdiff로 changelog/분류
            → Claude가 src/api 수정(lint/build green까지 자가복구)
            → 치팅 차단 → 경계밖 기계 되돌리기 → lint/build 최종 게이트
            → green이면 PR / 실패면 draft(needs-human)
            → 사람이 리뷰·머지
```

> 함께 읽기: 전체 구조·역할은 `blog-openapi-sync-architecture-deep-dive.md`, 정규화 한 줄은 `blog-openapi-spec-normalization.md` 참고.
