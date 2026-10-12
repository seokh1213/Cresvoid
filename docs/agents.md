# 에이전트 작업 규칙

Cresvoid의 공통 작업 규칙이다. 도구가 바뀌어도 이 파일과 `AGENTS.md`를 따른다.

## 1. 작업을 시작하고 끝내기

1. `AGENTS.md`, 이 파일, `docs/handoff.md`를 읽어라. 로컬 handoff가 없으면 만들어라.
2. handoff의 '진행 중'에서 다른 세션이 맡은 대상은 고치지 마라. 하루 넘게 기록이 멈춘 대상은 사용자에게 물은 뒤 넘겨받아라.
3. 관련 작업 기록과 `docs/decisions.md`를 읽어라. 이전 계획보다 최신 사용자 요청을 우선해라.
4. 시작 시각, 에이전트, 대상, 작업 기록 경로를 '진행 중'에 적어라.
5. 작업마다 `docs/log/YYYY-MM-DD-주제.md`를 만들고 요청, 판단, 변경, 검증, 남은 문제를 작업 중에 기록해라. 긴 실행은 실행 id와 결과 경로도 바로 적어라.
6. 끝내거나 멈추면 맡은 줄을 지우고 handoff의 상태와 남은 일을 고쳐라.

## 2. 기록과 공개 범위

- 작업 기록은 `docs/`에만 남겨라. 프로젝트 밖 기록 시스템은 쓰지 마라.
- `docs/log/`, `docs/handoff.md`, `docs/local/`, 학습자 공간, 도구별 개인 설정은 Git에 넣지 마라.
- 공개 문서에는 조직·내부 시스템·실제 운영 데이터·인증정보·개인 경로·도구 계정 정보를 넣지 마라.
- 한국어 문서는 해라체로 써라. 초안을 마친 뒤 설치된 `korean-writing-guide` 스킬을 정확히 한 번 적용해라. 특정 홈 디렉터리를 하드코딩하지 마라.
- 카드 번호는 `<종류>-<주제>-<순서>`다(D22). 색인은 직접 고치지 마라.
- 새 결정은 `docs/decisions.md`에 번호와 이유를 더해라.

## 3. 규칙 변경

- 프로젝트 규칙은 이 파일과 `AGENTS.md`에 둬라. 도구별 연결 파일이나 전역 설정에 복제하지 마라.
- 규칙을 바꾸면 작업 기록에 무엇을 왜 바꿨는지 적어라. 설계에 영향이 크면 결정에도 남겨라.
- 다른 세션의 규칙이 맞지 않아 보여도 지우기 전에 사용자에게 물어라. 사용자가 직접 변경을 지시한 범위는 그대로 진행해라.
- 파일을 고치기 직전에 다시 읽고 필요한 부분만 고쳐라. 큰 변경 전에는 `/tmp`에 사본을 만들어라.

## 4. 코드와 콘텐츠

- 엔진과 생성기·검사·변환 스크립트는 Kotlin이다. 엔진은 `engine/`의 Gradle 프로젝트이고 스크립트는 `.main.kts`다.
- 즉시 실행하는 `engine/bin/cresvoid`, `run.sh`, `build.sh`는 셸이다. 제작 워크플로우와 모의 실행기만 JavaScript다(D19).
- 엔진 실행 코드는 Kotlin 표준 라이브러리와 JDK 21만 쓴다. 승인된 의존성은 엔진 테스트의 Kotest와 코루틴 콘텐츠의 kotlinx-coroutines-core·test다(D16, D18, D21). 새 의존성은 사용자 승인을 받아라.
- Kotlin 코드를 쓰기 전에 설치된 `kotlin-core-best-practices`와 세션의 코드 규칙을 읽어라.
- 엔진 패키지는 cli, command, content, progress, study, runner, validation, json, terminal, project다. 저장·프로세스·출력·입력·시각은 인터페이스 뒤에 두고 `Main.kt`에서 조립해라(D18).
- 콘텐츠 계약은 `content/FORMAT.md`다. 제작과 확장은 `cresvoid-content` 및 종류별 스킬을 따라라.
- Kotlin 공통 파일은 `content/templates/kotlin/`가 원본이다. `kotlin content/templates/sync.main.kts`로 복사하고 `--check`로 확인해라.
- `practice/`의 학습자 코드와 진행 기록은 바꾸지 마라. 엔진 흐름 시험은 `/tmp` 사본에서 해라.

## 5. 실행과 검증

- 엔진 테스트: `engine/`에서 `./gradlew test`.
- 전체 흐름: `kotlin engine/e2e.main.kts`.
- 콘텐츠: `engine/bin/cresvoid validate <트랙>`.
- 템플릿: `kotlin content/templates/sync.main.kts --check`.
- 카드 색인: `kotlin content/ideas/index.main.kts --check`.
- 에이전트는 동시에 최대 2개다. 사용자가 순차 작업을 요청하면 `lanes: 1`로 호출 하나씩 실행해라.
- 출제자와 검증자는 나눠라. 검증자가 정답·참조 풀이·숨은 테스트를 보게 하지 마라.
- 병렬 워크플로우의 캐시로 재개하지 마라. 결과 기록에서 완료 여부를 확인하고 남은 호출만 실행해라(D7).
- 긴 작업 중 macOS가 잠들지 않게 `caffeinate -dims`를 사용해라. 덮개가 닫히면 실행이 끊길 수 있다.

## 6. 도구별 실행

스킬 원본은 `content/skills/`다. `.agents/skills`는 이 디렉터리를 가리킨다. 개인 도구 연결 파일은 로컬에서 따로 구성해라.

Workflow 도구가 있으면 `workflow.js`를 넘겨라. 없으면 `cresvoid-content`의 '워크플로우 도구 없이 실행하기' 절에 따라 호출을 실행해라. Codex에서는 서브에이전트 또는 새 `codex exec -C <프로젝트> -s workspace-write` 프로세스를 사용해라. 결과 schema는 `--output-schema`, 결과 파일은 `-o`로 지정해라. 저장소 밖의 `/tmp` 사본에서 실행할 때만 `--skip-git-repo-check`를 붙여라.

모의 실행은 `node content/skills/_shared/mock-workflow.mjs`다. 설치된 Node를 사용해라. 실제 제작을 처음 실행하면 막힌 곳과 해결 방법을 이 절 및 `cresvoid-content`에 기록해라. 범용 실행기 설계는 `content/skills/_shared/workflow-design.md`를 참고해라.
