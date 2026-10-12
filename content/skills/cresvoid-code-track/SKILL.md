---
name: cresvoid-code-track
description: cresvoid code 트랙(코드 읽기) 단계를 만든다. 카탈로그의 code 카드를 받아 안내 문서, 읽을 Kotlin 코드, 문항을 쓰고 실행 전 블라인드 풀이, antislop 검토, 수정, 재풀이로 검증한다. code 단계를 새로 만들 때 쓴다.
---

# code 단계 만들기

공통 절차(주제 고르기, 준비, 확인, 기록)는 `cresvoid-content` 스킬에 있다. 이 문서는 code 워크플로우만 설명한다.

## 흐름

단계마다 아래 순서로 진행한다. 단계들은 갈래로 나뉘어 동시에 진행한다.

| 순서 | agent | 하는 일 |
|---|---|---|
| 1 | 출제 | guide.md, `code/` Kotlin 프로젝트, `hidden/quiz.json` 을 쓴다. short 문항은 실행해 정답을 맞추고 choice 해설의 동작 주장도 실행으로 확인한다. 문항마다 목적과 함정 위치를 rationale 에, antislop Delivery Gate 를 gate 에 적는다 |
| 2 | 블라인드 풀이 | 정답 없이 guide.md 와 코드만 읽고 먼저 답한다(`first_answers`). 그다음 코드를 실행해 확인하고 다시 답한다(`final_answers`) |
| 3 | 검토 | antislop 기준으로 주석, 이름, 실무다움, 지어낸 API 를 검토한다. 학습자 관점으로 난도, 정답이 하나인지, 함정이 자연스러운지 검토한다 |
| 4 | 수정 | 블라인드 결과와 검토 지적을 반영한다 |
| 5 | 재풀이 | 새 agent 가 고친 단계를 다시 블라인드로 푼다 |
| 6 | 마지막 수정 | 재풀이에서 남은 문제가 있을 때만 실행한다 |

실행 전 첫 풀이에서 틀린 문항이 있으면 난도가 맞다. 실행 뒤 낸 최종 답이 정답과 다르면 문항이나 정답이 틀렸다.

## args

| 필드 | 필수 | 뜻 | 카드 항목 |
|---|---|---|---|
| `root` | 예 | cresvoid 절대 경로 | |
| `track` | 예 | 트랙 id | track |
| `lanes` | 아니오 | 동시에 진행할 갈래 수. 기본 2 | |
| `libs` | 아니오 | `kotlinx-coroutines` 면 프로젝트에 `lib/` 를 두고 kotlinx.coroutines 를 쓴다(FORMAT.md 7절, `docs/decisions.md` D21) | env |
| `stages[].dir` | 예 | 단계 디렉터리 | stage |
| `stages[].title` | 예 | 단계 제목 | title |
| `stages[].domain` | 예 | 코드가 푸는 문제와 상황 | 도메인 |
| `stages[].focus` | 예 | 문항이 겨냥할 Kotlin·표준 라이브러리 지점 | 겨냥 |
| `stages[].size` | 예 | 코드 규모. 예: `180~250줄, 파일 3~4개` | 형식 |
| `stages[].questions` | 예 | 문항 구성. 예: `5문항(실행 결과 예측 short 2, choice 3)` | 형식 |
| `stages[].level` | 예 | 난도 | 난도. 없으면 `content/ideas/implementation/README.md` 의 'code: 남의 코드를 끝까지 읽는다' 절 기준으로 쓴다 |
| `stages[].checks` | 아니오 | 검증 기준. 출제자와 검토자가 함께 본다 | 검증 |
| `stages[].avoid` | 아니오 | 피할 것. 출제자와 검토자가 함께 본다 | 피할 것 |
| `stages[].target_minutes` | 예 | 목표 풀이 시간(분) | 형식 |

## 실행 전

1. track.json 의 stages 에 `{ "dir", "title", "target_minutes" }` 를 더한다.
2. 새 트랙이면 track.json 을 `"kind": "code"` 로 만든다.

## 실행

실행 방법은 `cresvoid-content` 스킬의 '만들기' 절에 있다. 아래는 Workflow 도구에 넘기는 예다. 도구가 없는 에이전트도 같은 args 를 쓴다.

```
Workflow({
  scriptPath: "/path/to/cresvoid/content/skills/cresvoid-code-track/workflow.js",
  args: {
    root: "/path/to/cresvoid",
    track: "code-reading",
    stages: [{
      dir: "01-price-rules",
      title: "가격 규칙 계산",
      domain: "회원 등급 할인, 쿠폰, 묶음 할인을 장바구니에 적용해 결제 금액을 낸다",
      focus: "BigDecimal 의 scale 과 반올림 모드, equals 와 compareTo 의 차이, data class 의 copy 와 동등성, compareBy·thenBy 정렬, fold, sealed 타입과 when",
      size: "180~250줄, 파일 3~4개",
      questions: "5문항(실행 결과 예측 short 2, choice 3)",
      level: "숙련 Kotlin 개발자가 실행 없이 읽으면 한두 문항을 틀리는 수준",
      target_minutes: 20
    }]
  }
})
```

## 결과

`{ track, stages: [...] }` 를 돌려준다. 단계마다 아래 필드를 확인한다.

| 필드 | 뜻 | 확인할 것 |
|---|---|---|
| `rationale` | 문항 목적, 함정 위치, 구조를 고른 이유 | 이유가 없는 결정이 있는가 |
| `first_blind` | 문항별 정답, 첫 답, 최종 답, 확신도 | 첫 답을 모두 확신하며 맞혔으면 난도를 다시 검토한다 |
| `audit_findings`, `audit_gate` | 검토 지적과 Delivery Gate | FAIL 이 남지 않았는가 |
| `confirm` | 재풀이 결과 | 최종 답이 모두 정답과 같은가 |
| `leftovers` | 재풀이에서 남은 문제 | 비어 있어야 한다 |
| `gate` | 마지막 Delivery Gate | 모두 PASS 인가 |
| `remaining` | 수정 agent 가 남긴 걱정 | 하나씩 판단한다 |

## 실행 뒤

1. `cresvoid-content` 스킬의 "확인과 수정" 과 "기록" 을 따른다.
2. rationale 의 결정 이유를 `docs/log/` 기록에 옮긴다. 근거는 FORMAT.md 11.8 절이다.
