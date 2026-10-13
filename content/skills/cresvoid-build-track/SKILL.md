---
name: cresvoid-build-track
description: cresvoid build 트랙(구현) 단계를 만든다. 카탈로그의 build 카드를 받아 스펙을 쓴 뒤 모호성 검토, Kotlin 참조 풀이와 생성기, 참조 풀이와 독립적인 테스트와 문항, 교차 검증, 학습자 검토를 거친다. build 트랙을 새로 만들거나 단계를 더할 때 쓴다.
---

# build 단계 만들기

공통 절차(주제 고르기, 준비, 확인, 기록)는 `cresvoid-content` 스킬에 있다. 이 문서는 build 워크플로우만 설명한다.

## 흐름

build 단계는 앞 단계 위에 쌓인다. 그래서 한 트랙의 새 단계들을 한 번에 만든다.

| 순서 | agent | 하는 일 |
|---|---|---|
| 1 | 스펙 작성 | 새 단계의 spec.md 를 쓴다. 새 트랙이면 `starters/kotlin` 시작 코드도 만든다 |
| 2 | 모호성 검토 1 | 스펙만 보고 두 사람이 다르게 구현할 지점, 단계 사이 모순, 틀린 예시를 찾는다 |
| 3 | 스펙 수정 1 | 지적마다 규칙을 보태 해석을 하나로 고정한다. 지적이 없으면 건너뛴다 |
| 4 | 모호성 검토 2, 스펙 수정 2 | 남은 지적을 한 번 더 찾고 고친다. 1차에서 지적이 없었으면 건너뛴다 |
| 5 | 참조 풀이 | 단계마다 Kotlin `hidden/solution/` 과 `hidden/gen.main.kts` 를 만든다. 스펙 예시와 생성기 출력으로 실행해 확인한다 |
| 6 | 테스트 | 참조 풀이를 보지 않고 스펙에서 기대값을 유도해 `hidden/tests.json` 을 쓴다 |
| 6 | 문항 | 참조 풀이와 테스트를 보지 않고 `hidden/quiz.json` 을 쓴다. 테스트와 동시에 진행한다 |
| 7 | 교차 검증 | `cresvoid validate` 로 셋을 맞춰 보고 스펙 기준으로 틀린 쪽을 고친다. 통과할 때까지 반복한다 |
| 8 | 학습자 검토 | 난도, 명확성, 테스트 정합성, 문장 품질, 문항 품질을 검토한다. 파일은 고치지 않는다 |
| 9 | 마지막 수정 | must·should 지적이 있을 때만 실행한다 |

공개 API를 직접 구현하는 것이 목표라면 스펙에 호출 계약을 밝히고 `hidden/api-check.main.kts`로 빌드된 풀이를 호출한다(`content/FORMAT.md` 7절). 출력 테스트만으로 확인할 수 없는 동작을 검사하며, 7번에서 뒤 단계 참조 풀이도 누적 검증한다.

## args

| 필드 | 필수 | 뜻 | 카드 항목 |
|---|---|---|---|
| `root` | 예 | cresvoid 절대 경로 | |
| `track` | 예 | 트랙 id | track |
| `brief` | 예 | 설계 요약. 새 단계마다 목표, 명령과 규칙, 난도와 대규모 테스트 규모, 함정, 검증 방법을 적는다 | 목표, 다룰 내용, 난도, 함정, 검증 |
| `existing` | 아니오 | 이미 있는 단계 디렉터리 목록. 없으면 새 트랙으로 본다 | |
| `stages[].dir` | 예 | 새 단계 디렉터리 | stage |
| `stages[].title` | 예 | 새 단계 제목 | title |
| `lanes` | 아니오 | 2 면 테스트와 문항을 동시에 쓴다. 1 이면 차례로 쓴다. 기본 2 | |
| `libs` | 아니오 | `kotlinx-coroutines` 면 프로젝트에 `lib/` 를 두고 kotlinx.coroutines 를 쓴다(FORMAT.md 7절, `docs/decisions.md` D21) | env |

## 실행 전

1. track.json 의 stages 에 새 단계 `{ "dir", "title" }` 를 더한다.
2. 새 트랙이면 track.json 을 `"kind": "build"` 로 만든다.

## 실행

실행 방법은 `cresvoid-content` 스킬의 '만들기' 절에 있다. 아래는 Workflow 도구에 넘기는 예다. 도구가 없는 에이전트도 같은 args 를 쓴다.

```
Workflow({
  scriptPath: "/path/to/cresvoid/content/skills/cresvoid-build-track/workflow.js",
  args: {
    root: "/path/to/cresvoid",
    track: "search-engine",
    existing: ["01-boolean", "02-ranking", "03-phrase"],
    stages: [{ dir: "04-fuzzy", title: "오타 허용 검색" }],
    brief: "04-fuzzy: FUZZY <k> <낱말> 명령을 더한다. 낱말 단위 레벤슈타인 거리(삽입, 삭제, 교체 비용 1), 거리 상한 2, 정렬은 거리 오름차순, 문서 빈도 내림차순, 토큰 사전순이다. 기존 QUERY·SEARCH 출력은 바꾸지 않는다. 대규모 테스트는 어휘 20만 개, 질의 5천 개이고 모든 어휘와 거리를 계산하면 제한 시간을 넘어야 한다. 배경 개념으로 BK-트리와 동적 계획법 행 가지치기를 설명한다. 함정은 같은 거리의 정렬, 한글과 영문이 섞인 토큰, 거리 0 의 포함 여부다."
  }
})
```

## 결과

| 필드 | 뜻 | 확인할 것 |
|---|---|---|
| `spec.decisions` | 스펙 작성자가 정한 세부 규칙 | 카드 의도와 맞는가 |
| `hunted` | 모호성 검토 차수별 지적 수 | 2차 지적이 남았으면 직접 읽는다 |
| `reference.generators` | 생성기 인자와 대규모 실행 시간 | 제한 시간의 1/5 안인가 |
| `reference.spec_changes` | 참조 풀이 작성 중 고친 스펙 | 고친 이유가 맞는가 |
| `cross` | 교차 검증 결과 | `final_status` 가 `clean` 인가 |
| `review` | 학습자 검토 지적과 난도 판정 | must 가 남지 않았는가 |
| `finalFix` | 마지막 수정의 반영과 거절 | 거절 이유가 타당한가 |

## 실행 뒤

1. `cresvoid-content` 스킬의 "확인과 수정" 과 "기록" 을 따른다.
2. 블라인드 구현으로 스펙을 한 번 더 확인하려면 별도 agent 에게 spec.md 만 주고 구현하게 한 뒤 `cresvoid test` 로 실행한다. 이 확인은 /tmp 에 복사한 프로젝트에서 한다.
