---
name: cresvoid-content
description: cresvoid 컨텐츠를 만드는 공통 절차다. 카탈로그에서 주제를 고르고 종류별 워크플로우로 만든 뒤 검증하고 고쳐서 기록하는 순서를 정한다. cresvoid 트랙이나 단계를 새로 만들거나 늘릴 때 가장 먼저 읽는다.
---

# cresvoid 컨텐츠 만들기

모든 트랙 종류에 공통인 절차다. 종류별 출제 절차는 아래 스킬에 있다.

| 종류 | 스킬 | 블라인드 검증 |
|---|---|---|
| reading | `cresvoid-reading-track` | 정답을 모르는 agent 가 지문만 보고 푼다 |
| build | `cresvoid-build-track` | 참조 풀이와 테스트를 따로 만들어 교차 검증한다 |
| code | `cresvoid-code-track` | 실행 전에 읽기만으로 풀고 실행 뒤 다시 답한다 |
| fix | `cresvoid-fix-track` | 스펙과 시작 코드만 보고 고친 수정본을 숨은 테스트로 확인한다 |
| lab | `cresvoid-lab-track` | 스펙과 시작 코드만 보고 실험을 고친 수정본을 숨은 시나리오로 확인한다 |

## 원칙

1. 컨텐츠의 계약은 `content/FORMAT.md` 다. 카드나 스킬이 FORMAT.md 와 다르면 FORMAT.md 를 따른다.
2. 만드는 agent 와 검증하는 agent 를 나눈다.
3. 검증 agent 에게 정답, 참조 풀이, 숨은 테스트를 보여 주지 않는다. 셋은 단계의 `hidden/` 에 있다.
4. 정답과 기대값은 서로 독립된 agent 둘 이상이 따로 만든 뒤 맞춰 본다.
5. 계산, 코드 동작, 사실 서술은 실행이나 1차 자료로 확인한다.
6. 불일치가 나오면 스펙이나 지문을 기준으로 틀린 쪽을 고친다.
7. 테스트나 문항을 지워서 불일치를 없애지 않는다.
8. 고친 뒤에는 처음과 같은 방법으로 다시 검증한다.
9. 품질 관문은 `content/skills/_shared/quality-gates.md` 에 있다.

## 1. 주제 고르기

1. `content/ideas/INDEX.md` 의 "다음 후보" 에서 차수가 가장 낮고 env 결정이 끝난 카드를 고른다.
2. 카드와 그 도메인 README(`content/ideas/<영역>/README.md` 나 `content/ideas/backend/<도메인>/README.md`)를 읽는다. 도메인 README 의 개념 지도와 출처가 카드의 배경이다.
3. 카드에 빠진 항목이 있으면 먼저 채운다. 항목은 `content/ideas/README.md` 의 '카드 형식' 절에 있다.
4. 새 카드는 그 영역 README 의 출제 기준을 따른다. reading 카드는 `content/ideas/reading/README.md` 의 장르 순환 표에서 최근 세 단계에 없는 분야를 고른다.
5. 사실 확인이 필요한 주제는 카드를 쓸 때 1차 자료를 찾아 출처 항목에 적는다.
6. 카드의 status 를 `제작 중` 으로 바꾼다.
7. 단계 하나짜리 카드(reading, code, fix, lab)는 stage 앞에 `content/ideas/INDEX.md` 트랙 표의 다음 번호를 붙인다.
8. `kotlin content/ideas/index.main.kts` 를 실행한다.

## 2. 준비

1. 이미 있는 트랙이면 `engine/bin/cresvoid validate <트랙>` 이 지금 통과하는지 확인한다. 실패하면 원인부터 고친다.
2. `track.json` 에 새 단계를 더한다. 새 트랙이면 track.json 을 만든다(FORMAT.md 2절).
3. 단계 디렉터리와 그 안의 파일은 워크플로우가 만든다.

## 3. 만들기

1. 종류별 스킬의 SKILL.md 를 읽는다.
2. SKILL.md 의 args 표대로 카드 항목을 args 로 옮긴다.
3. 워크플로우를 실행한다. Workflow 도구가 있는 에이전트(Claude Code)는 아래처럼 넘긴다. 도구가 없는 에이전트(Codex 등)는 '워크플로우 도구 없이 실행하기' 절을 따른다.

```
Workflow({ scriptPath: "/path/to/cresvoid/content/skills/<스킬>/workflow.js", args: { ... } })
```

작업 디렉터리가 cresvoid 가 아닌 세션에서는 Workflow 도구가 그 경로를 거절한다. 그때는 workflow.js 내용을 `script` 로 넘긴다.

4. agent 는 동시에 두 개까지 실행한다.
5. 워크플로우는 단계를 갈래로 나눠 이 한도를 지킨다. 갈래 수는 args 의 `lanes` 이고 기본값은 2 다.
6. 워크플로우 두 개를 함께 실행할 때는 둘 다 `lanes: 1` 로 준다.
7. 멈춘 워크플로우를 앞 실행의 캐시(Workflow 도구의 `resumeFromRunId`)로 이어서 실행하지 않는다. 갈래가 병렬이면 캐시된 결과와 실제 파일이 어긋난다. 결정 기록은 `docs/decisions.md` D7 이다.
8. 멈췄으면 그 실행의 결과 기록(Workflow 도구는 `journal.jsonl`)을 읽고 끝나지 않은 단계만 다시 실행한다.
9. 실행 id 나 결과 디렉터리를 작업 기록에 바로 적는다(`docs/agents.md` 의 '작업을 맡고 내려놓을 때' 절).

## 워크플로우 도구 없이 실행하기

workflow.js 는 단계 정의로도 읽을 수 있다. `agent(...)` 호출 하나가 독립 에이전트 하나다. 호출 순서와 조건은 스크립트 코드에 있다. Workflow 도구가 없는 에이전트는 아래 순서로 같은 일을 직접 한다. Codex 로는 아직 실행해 보지 않았다. 처음 실행하는 세션은 막힌 곳과 푼 방법을 이 절에 더한다. 설계 이유와 실행기를 만들 때의 기준은 `content/skills/_shared/workflow-design.md` 에 있다.

1. 실행 디렉터리 `/tmp/cresvoid-run/<트랙>-<날짜>/` 를 만들고 args 를 `args.json` 으로 저장한다.
2. 모의 실행으로 프롬프트와 호출 순서를 뽑는다. `prompts.md` 에 호출마다 `### <label>` 과 그 프롬프트가 차례로 남는다. 출력의 `calls:` 아래 목록이 호출 순서다.

```sh
RUN=/tmp/cresvoid-run/<트랙>-<날짜>
MOCK_PROMPTS=$RUN/prompts.md node content/skills/_shared/mock-workflow.mjs content/skills/<스킬>/workflow.js "$(cat $RUN/args.json)" normal
```

3. 호출 하나마다 새 에이전트를 실행해 그 프롬프트를 준다. 한 에이전트가 출제와 검증을 이어서 맡지 않는다. Codex 는 서브에이전트를 쓰거나 `codex exec` 로 새 프로세스를 실행한다(`docs/agents.md` 의 '도구별 차이' 절).
4. 프롬프트가 열지 말라고 한 파일(정답, 참조 풀이, 숨은 테스트)은 그 에이전트가 보지 않게 한다. 블라인드 검증이 이 규칙에 기댄다.
5. 앞 호출의 결과가 들어가는 프롬프트(모호성 검토 뒤의 스펙 수정, 마지막 수정 등)에는 모의 실행의 가짜 값이 들어 있다. workflow.js 에서 그 프롬프트를 만드는 함수를 찾아 가짜 값 자리에 실제 결과 JSON 을 넣는다.
6. `schema` 가 있는 호출은 그 schema 모양의 JSON 으로 결과를 받는다. workflow.js 의 schema 상수를 JSON 파일로 옮겨 `codex exec --output-schema <파일> -o <결과 파일>` 로 넘긴다.
7. `parallel` 로 묶인 호출만 동시에 실행하고 동시에 두 개를 넘기지 않는다. 건너뛰기 조건(지적이 없으면 수정하지 않음 등)은 workflow.js 코드대로 따른다.
8. 결과를 `$RUN/<label>.json` 에 남긴다. 멈췄다가 다시 시작하면 결과 파일이 없는 호출부터 실행한다.
9. 스크립트가 마지막에 돌려주는 객체(`spec`, `cross`, `review`, `finalFix` 등)를 결과 파일로 직접 모은 뒤 '확인과 수정' 절로 넘어간다.

## 4. 확인과 수정

워크플로우 결과를 그대로 믿지 않는다. 실행한 agent 가 직접 확인한다.

1. `engine/bin/cresvoid validate <트랙>` 을 실행한다.
2. `kotlin content/templates/sync.main.kts --check` 를 실행한다.
3. `kotlin engine/e2e.main.kts` 를 실행한다.
4. 결과의 `remaining` 과 `leftovers` 에 남은 문제를 하나씩 읽는다.
5. 품질 관문을 통과하지 못한 문제는 고친다.
6. 블라인드 풀이가 정답과 다르게 답한 문항은 직접 다시 푼다. 문서만으로 정답이 하나로 정해지는지 확인한다.
7. 고칠 것이 크면 그 단계만 워크플로우로 다시 만든다.
8. 고칠 것이 작으면 직접 고치고 1~3을 다시 실행한다.

## 5. 기록

1. 카드의 status 를 `완료(YYYY-MM-DD)` 로 바꾸고 `kotlin content/ideas/index.main.kts` 로 INDEX.md 를 다시 만든다.
2. 영역 README 에 완료 표가 있으면(reading, implementation) 단계를 더한다.
3. 새 트랙을 만들었으면 `README.md` 의 트랙 표에 더한다. 트랙이나 단계를 더했으면 `content/README.md` 의 트랙 표를 고친다.
4. `docs/log/YYYY-MM-DD-<주제>.md` 에 워크플로우 run id, 단계별 블라인드 결과, 고친 내용, 남긴 문제를 적는다.
5. 새 결정은 `docs/decisions.md` 에 적는다.

## 워크플로우 고치기

1. 프롬프트 한 문장에 지시 하나를 담는다.
2. 프롬프트에는 그 단계에 필요한 규칙만 적는다.
3. 결과는 schema 로 받는다. 필수 필드는 다음 단계가 쓰는 것만 둔다.
4. `Date.now()`, `Math.random()`, 인자 없는 `new Date()` 는 쓰지 않는다.
5. 고친 뒤 모의 실행으로 확인한다.

```sh
node content/skills/_shared/mock-workflow.mjs content/skills/<스킬>/workflow.js '<args JSON>' normal
```

모의 실행은 agent 대신 schema 모양의 가짜 결과를 돌려준다. 호출 순서, 동시 실행 수, 프롬프트에 섞인 `undefined`, 금지된 시각·난수 호출을 찾아낸다. 오류가 있으면 종료 코드가 1 이다. 환경 변수 `MOCK_PROMPTS` 에 파일 경로를 주면 agent 마다 받은 프롬프트를 그 파일에 남긴다.

| 시나리오 | 바뀌는 것 |
|---|---|
| `normal` | 모든 agent 가 schema 기본값을 돌려준다 |
| `blind-fail` | fix 트랙 감사 agent 가 블라인드 실패를 돌려준다 |
| `author-null` | label 이 `:author` 로 끝나는 출제 agent 가 null 을 돌려준다 |

## 새 종류를 만들 때

1. `content/FORMAT.md` 에 형식을 정의한다.
2. 엔진에 문서 보기, 채점, 검증, 진행 기록을 더한다.
3. 가장 비슷한 스킬을 복사한다.
4. 출제, 블라인드 검증, 검토, 수정, 재확인 순서는 그대로 둔다.
5. 블라인드 검증은 학습자가 할 일을 정답 없이 해 보고 엔진의 채점으로 확인하는 방식으로 정한다.
6. 카탈로그의 영역이나 도메인 README 에 출제 기준을 적고 `cards/` 에 카드를 둔다.
7. 이 문서의 종류 표와 `content/skills/README.md` 에 스킬을 더한다.
