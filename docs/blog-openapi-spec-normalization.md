# 왜 스펙을 `python3 -m json.tool --sort-keys`로 정규화하나 — 초보자용 완전 해설

> 대상: JSON·diff·CI가 아직 낯선 초보 개발자.
> 다루는 한 줄:
> ```bash
> # 커밋 diff 안정화를 위해 키 정렬로 정규화
> python3 -m json.tool --sort-keys /tmp/openapi.json > /tmp/openapi.pretty.json
> ```
> 이 한 줄이 없으면 자동 동기화 하네스가 **오작동**한다. 왜 그런지, before/after 실제 모습으로 본다.

---

## 0. 3줄 요약 (TL;DR)

1. 백엔드가 뱉는 스펙(JSON)은 **내용이 같아도 실행마다 글자가 달라질 수 있다**(키 순서·압축 형태).
2. 그대로 두면 하네스가 "안 바뀐 걸 바뀌었다"고 오판 → **헛 PR·헛 알림**이 반복되고, 실제 변경 diff는 **한 줄이라 읽을 수도 없다**.
3. `json.tool --sort-keys`로 **"같은 내용 = 항상 똑같은 글자"** 로 만들면(정규화) diff가 정확·읽기 쉬워진다.

---

## 1. 먼저, 이 명령어가 뭘 하는지

`python3 -m json.tool` 은 파이썬에 **기본 내장된 JSON 정리 도구**다(따로 설치 안 해도 됨). JSON을 받아서 **보기 좋게 들여쓰기**해서 다시 뱉는다. 여기에 옵션을 하나 붙였다:

- `--sort-keys` : JSON 객체의 **키(key)를 알파벳 순으로 정렬**한다.

읽는 법: "`/tmp/openapi.json`을 읽어서 → 키 정렬 + 예쁘게 포맷 → `/tmp/openapi.pretty.json`에 저장". (`>` 는 결과를 파일로 저장하라는 뜻.)

이렇게 "형식을 하나로 통일하는 것"을 **정규화(normalization)** 라고 부른다. 목표는 딱 하나: **"내용이 같으면 글자도 100% 똑같게"** 만드는 것.

---

## 2. 왜 필요한가 — JSON은 "내용 같아도 글자가 다를 수 있다"

JSON에서 `{"a":1,"b":2}` 와 `{"b":2,"a":1}` 는 **의미가 완전히 같다**(순서는 상관없음). 그런데 **글자(byte)로는 다르다.**

문제는, 스펙을 만들어주는 springdoc이 실행할 때마다 이 **키 순서를 살짝 다르게** 뱉을 수 있다는 것(내부적으로 Map을 쓰는데 순서가 보장 안 되는 경우가 있음). 게다가 `/v3/api-docs` 원본은 보통 **압축된 한 줄**로 나온다.

그래서 **API를 하나도 안 고쳤는데도**, 어제 뽑은 스펙과 오늘 뽑은 스펙이 "글자로는 다른" 상황이 생긴다.

우리 하네스는 "스펙이 바뀌었나?"를 **글자 비교(`diff`)** 로 판정한다. 그러니 "내용은 같은데 글자가 다르다"를 그대로 두면 → **"바뀌었다!"고 오판**한다.

---

## 3. 장애 모습 ① — 같은 스펙인데 "바뀌었다"고 오판 (before)

같은 API를 두 번 실행했다고 하자. 내용은 같고 **키 순서만** 다르다(springdoc이 실제로 이럴 수 있음):

**RAW run1** (springdoc 원본 — 한 줄, 압축):
```json
{"openapi":"3.1.0","paths":{"/exhibitions":{"get":{"operationId":"list","tags":["Exhibition"]}}},"components":{"schemas":{"Exhibition":{"type":"object","properties":{"title":{"type":"string"},"id":{"type":"integer"}}}}}}
```
**RAW run2** (같은 API, 다음 실행 — `operationId`↔`tags`, `title`↔`id` 순서만 다름):
```json
{"paths":{"/exhibitions":{"get":{"tags":["Exhibition"],"operationId":"list"}}},"openapi":"3.1.0","components":{"schemas":{"Exhibition":{"properties":{"id":{"type":"integer"},"title":{"type":"string"}},"type":"object"}}}}
```

정규화 **없이** 이 둘을 `diff` 하면:
```
1c1
< {"openapi":"3.1.0","paths":{"/exhibitions":{"get":{"operationId":"list",...   ← 전체가 "달라짐"으로 뜸
---
> {"paths":{"/exhibitions":{"get":{"tags":["Exhibition"],"operationId":...
```
→ `diff`는 **"다르다"** 고 판정한다. 하네스 입장에선 `changed=true`.

**그래서 벌어지는 장애:**
- 백엔드는 실제 변경이 없는데도 **매번 "스펙 바뀜"으로 판정** → 프론트로 **헛 dispatch** → 프론트가 깨어나 **내용 없는 헛 PR**을 계속 만든다.
- 폴링(cron)까지 켜져 있으면 **매일** 이 헛 PR이 쌓인다. 노이즈 지옥.

---

## 4. 해결 — 둘 다 정규화하면 "완전히 같은 글자"가 된다 (after)

같은 두 파일을 `python3 -m json.tool --sort-keys`에 통과시키면, **키가 알파벳 순으로 정렬 + 예쁘게 포맷**되어 **완전히 동일한 결과**가 나온다:

**AFTER (정규화 결과 — run1도 run2도 이 모습으로 똑같아짐):**
```json
{
    "components": {
        "schemas": {
            "Exhibition": {
                "properties": {
                    "id": {
                        "type": "integer"
                    },
                    "title": {
                        "type": "string"
                    }
                },
                "type": "object"
            }
        }
    },
    "openapi": "3.1.0",
    "paths": {
        "/exhibitions": {
            "get": {
                "operationId": "list",
                "tags": [
                    "Exhibition"
                ]
            }
        }
    }
}
```

이제 정규화된 run1 vs run2를 `diff` 하면:
```
(출력 없음)
```
→ **완전히 동일** = `changed=false` = **조용히 종료**(dispatch·PR 안 함). 올바른 동작. ✅

`--sort-keys`가 핵심인 이유: 키를 항상 알파벳 순으로 강제하니까, **원래 순서가 어떻든 결과는 하나로 수렴**한다. "같은 내용이면 무조건 같은 글자".

---

## 5. 장애 모습 ② — 실제 변경도 "한 줄이라 읽을 수가 없다" (before → after)

정규화는 오판만 막는 게 아니라 **리뷰도 살린다.** 이번엔 **진짜로 바뀐** 경우(엔드포인트 `/exhibitions/featured` 추가):

**정규화 없이 raw끼리 diff (before):**
```
1c1
< {"openapi":"3.1.0","paths":{"/exhibitions":{"get":{...}}},"components":...   ← 옛날 스펙 전체(한 줄)
---
> {"openapi":"3.1.0","paths":{"/exhibitions":{"get":{...}},"/exhibitions/featured":{...}}},...   ← 새 스펙 전체(한 줄)
```
→ 실제 변경은 "featured 하나 추가"인데, **전체 줄이 통째로 바뀐 것처럼** 보인다. 리뷰어가 **뭐가 바뀐 건지 눈으로 못 찾는다.**

**정규화 후 diff (after):**
```diff
25a26,33
>         },
>         "/exhibitions/featured": {
>             "get": {
>                 "operationId": "featured",
>                 "tags": [
>                     "Exhibition"
>                 ]
>             }
```
→ **추가된 부분만 딱** 보인다(초록 `>`). 리뷰어가 "아, featured 엔드포인트가 생겼구나" 즉시 이해. 그리고 이 깔끔한 diff가 그대로 PR과 changelog에 실린다.

---

## 6. 정리 — 이 한 줄이 지키는 두 가지

| | 정규화 없음 (before) | `json.tool --sort-keys` (after) |
|---|---|---|
| **같은 스펙 재실행** | 글자가 달라 "바뀜"으로 오판 → 헛 PR 반복 | 항상 같은 글자 → `changed=false` → 조용히 끝 |
| **실제 스펙 변경** | 한 줄이 통째로 바뀐 것처럼 → 리뷰 불가 | 바뀐 부분만 깔끔히 → 리뷰·changelog 정확 |

한 문장으로: **"내용이 같으면 글자도 같게(정규화)"** 만들어야, "글자 비교(diff)"로 "내용이 바뀌었나"를 **정확하게** 판정할 수 있다. 이 한 줄이 하네스의 **변경 감지 정확도와 리뷰 가독성**을 동시에 떠받친다.

> 참고: 프론트 쪽 워크플로(`api-sync.yml`)도 새 스펙을 받을 때 똑같이 `python3 -m json.tool --sort-keys`로 정규화한 뒤 기준 스펙과 비교한다. 양쪽이 같은 정규화를 써야 diff가 맞아떨어진다.
