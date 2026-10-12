---
name: cresvoid-reading-track
description: cresvoid reading 트랙(비문학 독해) 단계를 만든다. 카탈로그의 reading 카드(일반 비문학 R 카드와 도메인 기술 지문 카드)를 받아 지문과 문항을 쓰고 정답을 모르는 agent 의 블라인드 풀이와 사실 확인으로 검증한 뒤 고친다. reading 단계를 새로 만들 때 쓴다.
---

# reading 단계 만들기

공통 절차(주제 고르기, 준비, 확인, 기록)는 `cresvoid-content` 스킬에 있다. 이 문서는 reading 워크플로우만 설명한다.

## 흐름

단계마다 아래 순서로 진행한다. 단계들은 갈래로 나뉘어 동시에 진행한다.

| 순서 | agent | 하는 일 |
|---|---|---|
| 1 | 출제 | passage.md 와 `hidden/quiz.json` 을 쓴다. 사실을 1차 자료로 확인하고 validate 를 통과시킨다 |
| 2 | 블라인드 풀이 | 정답 없이 지문만 보고 푼다. 문항마다 근거 문단, 확신도, 정답이 될 수 있는 다른 선지를 적는다 |
| 3 | 수정 | 정답과 다르게 푼 문항, 확신이 낮은 문항, 정답이 둘로 읽히는 문항을 고친다. 사실 서술을 다시 확인한다 |
| 4 | 재풀이 | 새 agent 가 고친 단계를 다시 블라인드로 푼다 |
| 5 | 마지막 수정 | 재풀이에서 남은 문제가 있을 때만 실행한다 |

## args

| 필드 | 필수 | 뜻 | 카드 항목 |
|---|---|---|---|
| `root` | 예 | cresvoid 절대 경로 | |
| `track` | 아니오 | 트랙 id. 기본 `reading`. 도메인 기술 지문은 `tech-reading` | track |
| `lanes` | 아니오 | 동시에 진행할 갈래 수. 기본 2 | |
| `stages[].dir` | 예 | 단계 디렉터리 | stage |
| `stages[].topic` | 예 | 분야와 주제 | title, 분야 |
| `stages[].scope` | 예 | 지문이 담을 개념과 규칙 | 목표, 다룰 내용 |
| `stages[].length` | 예 | 공백 포함 글자 수 범위. 예: `2,700~3,100` | 형식 |
| `stages[].paragraphs` | 예 | 문단 수. 예: `7~8` | 형식 |
| `stages[].questions` | 예 | 문항 수 | 형식 |
| `stages[].mix` | 예 | 문항 구성 | 형식, `content/ideas/reading/README.md` 의 '출제 기준' 절 기본 구성 |
| `stages[].level` | 예 | 난도 | 난도 |
| `stages[].traps` | 아니오 | 문항이 노릴 지점 | 함정과 엣지 케이스 |
| `stages[].sources` | 아니오 | 사실 확인에 쓸 1차 자료 | 출처 |
| `stages[].avoid` | 아니오 | 피할 것 | 피할 것 |

## 실행 전

1. track.json 의 stages 에 `{ "dir", "title", "target_minutes" }` 를 더한다.
2. `target_minutes` 는 카드 형식의 목표 시간이다.

## 실행

실행 방법은 `cresvoid-content` 스킬의 '만들기' 절에 있다. 아래는 Workflow 도구에 넘기는 예다. 도구가 없는 에이전트도 같은 args 를 쓴다.

```
Workflow({
  scriptPath: "/path/to/cresvoid/content/skills/cresvoid-reading-track/workflow.js",
  args: {
    root: "/path/to/cresvoid",
    stages: [{
      dir: "04-enzyme",
      topic: "과학(생명, 화학): 효소 반응 속도와 저해",
      scope: "효소-기질 복합체와 반응 속도, 미하엘리스-멘텐 식, Km 의 뜻, 경쟁적·비경쟁적·반경쟁적 저해, 이중 역수 그래프 해석",
      length: "2,700~3,100",
      paragraphs: "7~8",
      questions: 6,
      mix: "내용 일치 1, 추론 1, 개념 비교 1, <보기> 적용 2(저해제 속도 표, 이중 역수 그래프 절편), 종합 1",
      level: "수능 과학 기술 지문 최고난도",
      traps: "경쟁적 저해에서 Vmax 가 줄어든다는 착각, Km 과 친화도의 방향, x 절편 -1/Km",
      sources: "Berg 외 Biochemistry(NCBI Bookshelf), Lehninger Principles of Biochemistry",
      avoid: "혼합형 저해를 섞는 것"
    }]
  }
})
```

## 결과

`{ track, stages: [...] }` 를 돌려준다. 단계마다 아래 필드를 확인한다.

| 필드 | 뜻 | 확인할 것 |
|---|---|---|
| `passage_chars` | 출제자가 센 글자 수 | 카드 범위 안인가 |
| `first_agreement` | 첫 블라인드 풀이가 정답과 같은 문항 수 | 낮으면 수정 내용을 자세히 읽는다 |
| `facts_checked` | 확인한 사실과 출처 | 출처 없는 사실이 남았는가 |
| `confirm` | 재풀이 결과 | 모든 문항이 정답과 같은가 |
| `leftovers` | 재풀이에서 남은 문제 | 비어 있어야 한다 |
| `remaining` | 수정 agent 가 남긴 걱정 | 하나씩 판단한다 |
| `difficulty` | 블라인드 풀이 agent 의 난도 평가 | 카드 난도와 맞는가 |

## 실행 뒤

1. `cresvoid-content` 스킬의 "확인과 수정" 과 "기록" 을 따른다.
2. `reading` 트랙이면 `content/ideas/reading/README.md` 의 장르 순환 표와 완료 표를 고친다. `tech-reading` 트랙은 고칠 표가 없다.
