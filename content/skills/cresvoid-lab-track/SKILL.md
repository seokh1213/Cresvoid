---
name: cresvoid-lab-track
description: cresvoid lab 트랙의 실험 단계를 만든다. 스펙, Kotlin HTTP 서비스, 독립 퀴즈, 블라인드 구현, 실험 시나리오를 교차 검증할 때 쓴다.
---

# lab 단계 만들기

주제 선택과 작업 기록은 `cresvoid-content`를 따른다. 파일 형식은 `content/FORMAT.md`, 실제 실행 계약은 엔진의 `LabSuite`와 `LabHarness`가 기준이다.

## 제작 순서

1. 출제자가 `spec.md`, `starter/`, `hidden/solution/`, `hidden/lab.json`, `hidden/experiment.main.kts`, 실패 변형을 만든다. 스펙에는 관찰 순서, 바꿀 코드, 요청 부하, 측정값, 합격 불변식을 번호 문단으로 적는다. 시작 코드와 실패 변형은 빌드되고 실험도 끝나되 불변식에서 실패해야 한다.
2. 독립 퀴즈 작성자는 `spec.md`만 보고 `hidden/quiz.json`을 쓴다. 별도 블라인드 구현자는 `spec.md`와 `starter/`만 보고 `/tmp` 사본을 고친다. 두 사람에게 참조 풀이와 `hidden/`의 기존 파일을 보여 주지 않는다.
3. 교차 검증자가 참조 풀이, 시작 코드, 실패 변형, 블라인드 구현을 실험하고 `cresvoid validate <트랙>`을 실행한다. 스펙을 기준으로 차이를 고친다. 공개 스펙이나 시작 코드를 고쳤으면 새로운 블라인드 구현으로 다시 확인한다.
4. 학습자 검토자가 설명, 코드 수정 범위, 지표와 문항의 이해 가능성을 검토한다. 수정 담당자는 타당한 지적을 반영한 뒤 같은 검증을 다시 실행한다. 완료 전 공통 확인과 기록은 `cresvoid-content`를 따른다.

`hidden/experiment.main.kts`는 `hidden/`을 작업 디렉터리로 삼아 `kotlin <스크립트> <프로그램 절대 경로> <시나리오 이름>`으로 실행된다. 이 스크립트가 서비스를 띄우고 종료하며 HTTP 부하를 보내고 통계를 모은다. 표준 출력은 고유한 `METRIC <이름> <유한한 숫자>` 줄 최대 10개다. 종료 코드 0은 통과, 1은 불변식 실패, 그 밖은 실험 오류다. 시나리오마다 `refs`, 해당 문단의 정확한 `quote`, 양수 `timeout_sec`와 `repeat`를 `lab.json`에 적는다. `must_fail`은 `variants/<slug>` 경로 목록이다. 새 의존성을 요구하지 않는다. JDK 21과 이미 승인된 Kotlin·kotlinx.coroutines만 쓴다.

## 워크플로우 인자

| 필드 | 필수 | 뜻 |
|---|---|---|
| `root` | 예 | cresvoid 절대 경로 |
| `track` | 예 | 트랙 id |
| `brief` | 예 | 실험 목표, 부하, 측정값, 합격 기준, 실패 변형 설계 |
| `stages[].dir` | 예 | `01-dispatcher-starvation` 같은 단계 디렉터리 |
| `stages[].title` | 예 | 단계 제목 |
| `lanes` | 아니오 | 동시 단계 수 1 또는 2. 기본 1. 순차 작업은 1 |

`track.json`의 `kind`는 `lab`로 두고 단계를 등록한 뒤 워크플로우를 실행한다. 워크플로우는 `content/skills/cresvoid-lab-track/workflow.js`다. Workflow 도구가 없으면 `cresvoid-content`의 수동 실행 절차를 따른다. JavaScript는 이 제작 워크플로우와 모의 실행기에만 쓴다. 제품 코드와 실험 스크립트는 Kotlin이다.

## 결과 확인

결과는 `{ track, stages: [...] }`다. 각 단계의 `author`, `quiz`, `blind`, `cross`, `review`, `fix`, `confirm`, `remaining`을 확인한다. `cross.final_status`와 마지막 `confirm.final_status`가 `clean`인지, `blind_passed`가 참인지, `remaining`에 문제가 없는지 본다. `failed`가 있으면 해당 호출의 파일과 기록부터 확인한다. 모의 실행은 다음 명령으로 검사한다.

```sh
node content/skills/_shared/mock-workflow.mjs content/skills/cresvoid-lab-track/workflow.js '{"root":"/tmp/cresvoid","track":"coroutine-lab","brief":"HTTP 부하와 동시 실행 불변식을 검증한다","stages":[{"dir":"01-dispatcher-starvation","title":"블로킹 호출과 디스패처 고갈"}],"lanes":1}' normal
```
