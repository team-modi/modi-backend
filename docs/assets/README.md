# API 자동 동기화 하네스 — 포트폴리오 에셋 (실동작 스크린샷)

> 2026-07-05 라이브 E2E 시연에서 캡처한 실제 동작 화면. 백엔드에 임시 엔드포인트(`GET /exhibitions/featured`)를 머지 → 사람 개입 0으로 프론트에 sync PR + 슬랙 알림까지 자동 실행되는 전 과정.

| 파일 | 무엇을 보여주나 | 원본(공개 URL) |
|---|---|---|
| `01-backend-publish-run.png` | **백엔드 발행 워크플로** `publish-openapi` — 앱 부팅 → `/v3/api-docs` 덤프 → `openapi-spec` 브랜치 발행 → 프론트로 dispatch, 전 스텝 초록 (1m 2s) | github.com/team-modi/modi-backend/actions/runs/28740042390 |
| `02-frontend-sync-run.png` | **프론트 동기화 워크플로** `api-sync` — `repository_dispatch`로 자동 기동 → oasdiff → **AI sync(Claude Code)** → **anti-cheat** → **결정론적 가드레일** → lint+build → PR 생성 → **Notify Slack**, 전 스텝 초록 (2m 42s) | github.com/team-modi/modi-frontend/actions/runs/28740076525 |
| `03-sync-pr-diff.png` | **자동 생성된 sync PR** `#32` — github-actions 봇이 `api-sync/pending` → `develop`로, `src/api` + 기준 스펙 변경 | github.com/team-modi/modi-frontend/pull/32 |
| `04-slack-notification.png` | **슬랙 알림** — 분류 체크리스트(부연설명) · 백엔드 커밋 작성자 · 추가/변경/삭제 API 개수 · 변경 목록 · PR 링크 (`api-sync/templates/slack-notify.md` 템플릿으로 렌더) | (비공개 채널 알림을 동일 포맷으로 재현) |

## 한 줄 흐름
```
백엔드 develop 머지 → publish-openapi(부팅·덤프·발행·dispatch)
  → api-sync(oasdiff·AI 수정·가드레일·검증·PR) → 슬랙 알림 → 사람 리뷰·머지
```

## 참고
- `01`·`02`·`03`은 **실제 공개 레포의 라이브 실행 화면**을 헤드리스 브라우저(Playwright)로 캡처.
- `04`는 비공개 슬랙 채널로 실제 발송된 알림을 동일 템플릿 포맷으로 재현(실제 채널에도 동일 메시지 존재).
- 관련 문서: `blog-openapi-sync-architecture-deep-dive.md`(구조), `blog-api-sync-workflow-explained.md`(워크플로 해설), `blog-openapi-frontend-auto-sync.md`(구축기).
