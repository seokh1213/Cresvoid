# skills

cresvoid 컨텐츠를 만드는 절차를 스킬로 모았다. 지금 있는 트랙은 모두 이 절차로 만들었다.

| 스킬 | 만드는 것 | 워크플로우 |
|---|---|---|
| [cresvoid-content](cresvoid-content/SKILL.md) | 공통 절차. 주제 고르기, 준비, 확인과 수정, 기록 | 없음 |
| [cresvoid-reading-track](cresvoid-reading-track/SKILL.md) | reading 단계. 비문학 지문과 문항 | `workflow.js` |
| [cresvoid-build-track](cresvoid-build-track/SKILL.md) | build 단계. 스펙, Kotlin 참조 풀이, 생성기, 테스트, 문항 | `workflow.js` |
| [cresvoid-code-track](cresvoid-code-track/SKILL.md) | code 단계. 안내 문서, 읽을 Kotlin 코드, 문항 | `workflow.js` |
| [cresvoid-fix-track](cresvoid-fix-track/SKILL.md) | fix 단계. 스펙과 버그 신고, 참조 풀이, 버그를 심은 시작 코드, 테스트 | `workflow.js` |

`_shared/` 에는 모든 스킬이 쓰는 파일이 있다.

| 파일 | 내용 |
|---|---|
| [quality-gates.md](_shared/quality-gates.md) | 단계를 끝내기 전에 확인하는 품질 관문 |
| [workflow-design.md](_shared/workflow-design.md) | 워크플로우 설계 원칙. 도구가 바뀌어도 지킬 것과 실행기를 만들 때의 기준 |
| `mock-workflow.mjs` | workflow.js 모의 실행 도구. node 로 실행한다 |

workflow.js 와 mock-workflow.mjs 는 JavaScript 다. Claude Code 의 Workflow 도구가 JavaScript 만 받기 때문이다. 프로젝트의 다른 코드는 모두 Kotlin 이다. Workflow 도구가 없는 에이전트(Codex 등)는 workflow.js 를 단계 정의로 읽고 같은 순서를 직접 실행한다. 방법은 cresvoid-content 의 '워크플로우 도구 없이 실행하기' 에 있다.

## 쓰는 법

1. `content/ideas/INDEX.md` 의 "다음 후보" 에서 카드를 고른다.
2. [cresvoid-content](cresvoid-content/SKILL.md) 를 읽는다.
3. 카드 종류에 맞는 스킬의 SKILL.md 를 읽는다.
4. SKILL.md 의 args 표대로 카드 항목을 옮겨 워크플로우를 실행한다.

`.agents/skills`는 이 디렉터리를 가리키는 심볼릭 링크다. 다른 도구의 연결 파일은 로컬에서 구성한다. 스킬 원본은 이 디렉터리 하나다.

## 늘리는 법

- 같은 종류의 단계를 더 만들 때는 카탈로그에 카드를 더하고 스킬을 그대로 쓴다.
- 새 트랙 종류는 엔진과 `content/FORMAT.md` 를 먼저 고친 뒤 스킬을 만든다. 절차는 cresvoid-content 의 "새 종류를 만들 때" 에 있다.
- 다음 후보는 lab 종류다. 설계는 `content/ideas/backend/lab.md` 에 있다.
- workflow.js 를 고치면 모의 실행으로 확인한다. 방법은 cresvoid-content 의 "워크플로우 고치기" 에 있다.
