---
name: cresvoid-fix-track
description: cresvoid fix 트랙(버그 수정) 단계를 만든다. 카탈로그의 fix 카드를 받아 스펙과 버그 신고, 참조 풀이, 버그 하나를 심은 시작 코드, 테스트를 쓰고 블라인드 수정, antislop 검토, 수정, 재확인으로 검증한다. fix 단계를 새로 만들 때 쓴다.
---

# fix 단계 만들기

공통 절차(주제 고르기, 준비, 확인, 기록)는 `cresvoid-content` 스킬에 있다. 이 문서는 fix 워크플로우만 설명한다.

## 흐름

단계마다 아래 순서로 진행한다. 단계들은 갈래로 나뉘어 동시에 진행한다.

| 순서 | agent | 하는 일 |
|---|---|---|
| 1 | 출제 | spec.md 앞부분 → `hidden/solution/` → 버그 하나를 고른 starter/ → 버그 신고 절 → `hidden/tests.json` 순서로 쓴다. validate 를 통과시킨다 |
| 2 | 블라인드 수정 | spec.md 와 starter/ 만 보고 /tmp 사본에서 버그를 찾아 고친다. `hidden/` 은 보지 않는다 |
| 3 | 감사 | /tmp 프로젝트 사본에서 블라인드 수정본을 `cresvoid test` 로 숨은 테스트에 실행한다. antislop 기준과 학습자 관점으로도 검토한다 |
| 4 | 수정 | 감사 결과를 반영한다. 버그를 바꿨으면 `bug_changed` 를 true 로 돌려준다 |
| 5 | 재확인 | 블라인드 수정이 실패했거나, 버그를 바꿨거나, must 지적이 있었을 때만 블라인드 수정과 감사를 한 번 더 한다 |
| 6 | 마지막 수정 | 재확인에서도 실패하거나 must 지적이 남았을 때만 실행한다 |

블라인드 수정본이 숨은 테스트를 통과하면 버그 신고와 스펙만으로 원인에 닿을 수 있다. 다른 방식으로 고친 수정본이 테스트를 통과하지 못하면 테스트가 구현 세부에 기대고 있다.

## args

| 필드 | 필수 | 뜻 | 카드 항목 |
|---|---|---|---|
| `root` | 예 | cresvoid 절대 경로 | |
| `track` | 예 | 트랙 id | track |
| `lanes` | 아니오 | 동시에 진행할 갈래 수. 기본 2 | |
| `libs` | 아니오 | `kotlinx-coroutines` 면 프로젝트에 `lib/` 를 두고 kotlinx.coroutines 를 쓴다(FORMAT.md 7절, `docs/decisions.md` D21) | env |
| `stages[].dir` | 예 | 단계 디렉터리 | stage |
| `stages[].title` | 예 | 단계 제목 | title |
| `stages[].context` | 예 | 제품 맥락. 카드에 버그 신고 예가 있으면 끝에 덧붙인다 | 맥락, 버그 신고 |
| `stages[].bugs` | 예 | 버그 후보. 출제 agent 가 하나를 고른다 | 버그 후보 |
| `stages[].size` | 예 | 코드 규모. 예: `220~320줄, 파일 3~4개` | 형식 |
| `stages[].level` | 예 | 난도 | 난도. 없으면 `content/ideas/implementation/README.md` 의 'fix: 버그를 재현하고 고친다' 절 기준으로 쓴다 |
| `stages[].checks` | 아니오 | 검증 기준. 출제자와 검토자가 함께 본다 | 검증 |
| `stages[].avoid` | 아니오 | 피할 것. 출제자와 검토자가 함께 본다 | 피할 것 |

## 실행 전

1. track.json 의 stages 에 `{ "dir", "title" }` 를 더한다.
2. 새 트랙이면 track.json 을 `"kind": "fix"` 로 만든다.

## 실행

실행 방법은 `cresvoid-content` 스킬의 '만들기' 절에 있다. 아래는 Workflow 도구에 넘기는 예다. 도구가 없는 에이전트도 같은 args 를 쓴다.

```
Workflow({
  scriptPath: "/path/to/cresvoid/content/skills/cresvoid-fix-track/workflow.js",
  args: {
    root: "/path/to/cresvoid",
    track: "bug-fix",
    stages: [{
      dir: "02-point-accrual",
      title: "포인트 적립 계산",
      context: "결제 금액과 적립률로 포인트를 계산하고 일별 정산 합계를 낸다",
      bugs: "Double 연산 뒤 내림으로 1포인트가 모자람, BigDecimal 의 equals 로 중복 판정 실패, 반올림 모드 차이, 누적 합계의 Int 오버플로",
      size: "220~320줄, 파일 3~4개",
      level: "증상에서 원인까지 두세 단계 추론이 필요한 수준"
    }]
  }
})
```

## 결과

`{ track, stages: [...] }` 를 돌려준다. 단계마다 아래 필드를 확인한다.

| 필드 | 뜻 | 확인할 것 |
|---|---|---|
| `bug` | 고른 버그 | 카드 후보에서 골랐는가 |
| `rationale` | 버그를 고른 이유, 원인에 닿는 추론 경로 | 증상에서 원인까지 길이 있는가 |
| `blind_cause`, `blind_passed` | 블라인드 수정자가 찾은 원인과 숨은 테스트 결과 | 원인이 출제 의도와 같은가 |
| `audit_findings` | 감사 지적 | must 가 남지 않았는가 |
| `confirm_passed` | 재확인 결과. 재확인을 하지 않았으면 null | true 이거나 null 인가 |
| `gate` | 마지막 Delivery Gate | 모두 PASS 인가 |
| `remaining` | 수정 agent 가 남긴 걱정 | 하나씩 판단한다 |

## 실행 뒤

1. `cresvoid-content` 스킬의 "확인과 수정" 과 "기록" 을 따른다.
2. `cresvoid validate` 출력의 "바뀐 줄 수" 를 확인한다. FORMAT.md 11.7 절대로 버그 수정은 몇 줄로 끝나야 한다.
