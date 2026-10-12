# 컨텐츠 카탈로그

cresvoid 가 기르려는 역량, 그 역량을 훈련할 트랙, 앞으로 만들 단계를 모은 지도다. agent 는 카드 한 장과 그 도메인 README 만 보고 컨텐츠를 만들 수 있어야 한다. 카드에 빠진 정보가 있으면 컨텐츠를 만들기 전에 카드를 먼저 채운다.

## 1. 구조

```
content/ideas/
├── README.md          이 문서. 역량 지도, 카드 형식, 차수, 절차
├── INDEX.md           모든 카드 목록. index.main.kts 가 만든다
├── index.main.kts     카드 검사와 INDEX.md 생성
├── reading/           읽기 역량. README.md(출제 기준), cards/
├── implementation/    구현 역량. README.md(build·code·fix 출제 기준), cards/
└── backend/           실무 역량. README.md(도메인 지도), lab.md(lab 종류 설계), 도메인 폴더
    └── <도메인>/      README.md(개념 지도, 학습 경로, 환경, 아이디어, 출처), cards/
```

- 영역과 도메인 README 는 출제 기준과 조사 결과를 담는다. 카드는 그 기준 위에서 단계 하나를 만드는 데 필요한 내용을 담는다.
- 카드 목록과 상태는 [INDEX.md](INDEX.md) 에서 확인한다. INDEX.md 는 손으로 고치지 않는다.

## 2. 역량 지도

| 역량 | 무엇을 기르나 | 트랙 종류 | 안내 |
|---|---|---|---|
| 읽기 | 긴 한국어 비문학을 정확히 읽고 조건, 인과, 예외를 따라가는 힘 | reading | [reading/README.md](reading/README.md) |
| 구현 | AI 없이 스펙을 읽고 설계해 끝까지 구현하는 힘, 남의 코드를 읽고 고치는 힘 | build, code, fix | [implementation/README.md](implementation/README.md) |
| 실무 | 백엔드, 시스템 설계, 데이터 플랫폼, 검색, AI 에이전트 같은 도메인의 원리와 엣지 케이스를 설명하고 다루는 힘, 공개 장애 보고서의 원인 사슬을 따라가는 힘 | 다섯 종류 모두. lab 은 새 종류다 | [backend/README.md](backend/README.md) |

## 3. 카드 형식

카드 하나는 파일 하나다. 경로는 `<영역>/cards/<id>-<영문 slug>.md` 나 `<영역>/<도메인>/cards/<id>-<영문 slug>.md` 다. 파일은 front matter 로 시작한다.

```
---
id: B-spark-20
title: 데이터 흐름 엔진 만들기
kind: build
track: dataflow
stage: 01-plan, 02-shuffle, 03-join, 04-adaptive
status: 후보
wave: 2
env: 없음
prereq: R-spark-10
---
```

| 필드 | 필수 | 뜻 |
|---|---|---|
| id | 예 | `<종류>-<주제>-<순서>`. 규칙은 '카드와 도메인 늘리기' 절에 있다 |
| title | 예 | 단계 제목. track.json 의 단계 제목으로 쓴다 |
| kind | 예 | reading, build, code, fix, lab 가운데 하나. 같은 트랙의 카드는 종류가 같다 |
| track | 예 | 트랙 id(영문 소문자 kebab-case) |
| stage | 예 | 단계 디렉터리 이름. build 카드는 `두 자리 번호-영문 slug` 를 쓰고 쉼표로 여럿 적을 수 있다. 단계 하나짜리 카드는 만들기 전에는 영문 slug 만 적고 제작 중으로 바꿀 때 [INDEX.md](INDEX.md) 트랙 표의 다음 번호를 붙인다 |
| status | 예 | `후보`, `제작 중`, `완료(YYYY-MM-DD)`, `보류(이유)` |
| wave | 예 | 차수 1~5. '차수' 절 |
| env | 아니오 | 추가로 필요한 도구. `없음` 이나 [backend/README.md](backend/README.md) 의 '환경' 절의 값을 쉼표로 적는다 |
| prereq | 아니오 | 먼저 끝내면 좋은 카드 id 목록 |

본문은 `# <id> <제목>` 아래에 `- 항목: 내용` 꼴로 쓴다. 종류마다 쓰는 항목은 다음과 같다. 해당하지 않는 항목은 뺀다.

| 종류 | 항목 |
|---|---|
| reading | 분야, 목표, 다룰 내용, 난도, 형식, 함정과 엣지 케이스, 검증, 출처, 피할 것. 장애 보고서 카드는 사건, 재발 방지책, 이어지는 카드를 더한다 |
| build | 목표, 입출력 모양, 단계별 내용, 난도, 함정, 검증, 출처, 피할 것 |
| code | 도메인, 겨냥, 형식, 난도, 검증, 출처, 피할 것 |
| fix | 맥락, 버그 후보, 버그 신고, 형식, 난도, 검증, 출처, 피할 것 |
| lab | 모드, 실행 방식, 목표, 실험이나 시나리오, 예측 문항, 불변식이나 관측, 출처 |

## 4. 차수

차수는 만들 순서의 묶음이다. 같은 차수 안에서는 env 결정이 필요 없는 카드를 먼저 만든다.

| 차수 | 뜻 |
|---|---|
| 1 | 지금 만드는 단계. code-reading 과 bug-fix 첫 세 단계 |
| 2 | 엔진 작업 없이 바로 만들 수 있는 실무 도메인 1차. Spark, HDFS, OLAP, Elasticsearch, AI 에이전트의 기술 지문과 구현 트랙 |
| 3 | lab 종류 엔진 작업과 k6·Redis 설치 뒤의 첫 실험(L-load-10~L-load-60), 도메인 구현 트랙 2차, 시스템 설계 핵심 구현(B-system-design-20, B-system-design-30, B-system-design-40, B-system-design-50)과 규모 추정 지문, 코루틴 지문과 활용 구현, 코드 읽기, 버그 수정(R-coroutines-10, B-coroutines-20, C-coroutines-30, F-coroutines-31), 장애 보고서 지문 1차 |
| 4 | Docker, Maven 결정 뒤의 도메인 실험과 spring-lab 1차, 읽기와 구현의 후속 단계, 도메인 기술 지문 2차, 코루틴 활용 심화(B-coroutines-35)와 실행기 구현(B-coroutines-40), 장애 보고서 지문 2차 |
| 5 | 그 뒤의 단계와 보류된 카드 |

## 5. 카드로 컨텐츠를 만드는 절차

1. [INDEX.md](INDEX.md) 의 "다음 후보" 에서 차수가 가장 낮고 env 결정이 끝난 카드를 고른다.
2. 카드의 status 를 `제작 중` 으로 바꾼다. 단계 하나짜리 카드는 stage 앞에 [INDEX.md](INDEX.md) 트랙 표의 다음 번호를 붙인다. 그다음 `kotlin content/ideas/index.main.kts` 를 실행한다.
3. 종류에 맞는 스킬을 읽는다. 공통 절차는 `content/skills/cresvoid-content` 에 있다. reading 은 `content/skills/cresvoid-reading-track`, build 는 `content/skills/cresvoid-build-track`, code 는 `content/skills/cresvoid-code-track`, fix 는 `content/skills/cresvoid-fix-track` 이다.
4. `content/FORMAT.md` 를 읽는다. 카드가 FORMAT.md 와 다르면 FORMAT.md 를 따르고 다른 점을 카드에 적는다.
5. 카드 항목과 도메인 README 의 개념 지도를 스킬 워크플로우의 args 로 옮긴다.
6. track.json 에 단계를 더하고 워크플로우를 실행한다. 끝나면 `cresvoid validate <트랙>` 과 `kotlin engine/e2e.main.kts` 를 실행한다.
7. 카드의 status 를 `완료(YYYY-MM-DD)` 로 바꾸고 색인을 다시 만든다. `docs/log/` 에 기록을 남긴다.

lab 종류는 아직 엔진과 스킬이 없다. [backend/lab.md](backend/lab.md) 의 엔진 작업을 끝낸 뒤 다른 스킬을 본떠 `content/skills/cresvoid-lab-track` 을 만든다.

## 6. 카드와 도메인 늘리기

- 카드 id 는 `<종류>-<주제>-<순서>` 다. 종류는 R(reading), B(build), C(code), F(fix), L(lab) 이다.
- 실무 도메인 카드의 주제는 도메인 폴더 이름이다.
- 구현 영역에서 build 카드의 주제는 트랙 이름이다. code·fix 카드의 주제는 kotlin 이다.
- 읽기 카드의 주제는 카드의 '분야' 항목을 영문으로 옮긴 이름이다. 과학 science, 사회 society, 기술 technology, 인문 humanities, 경제 economics, 법 law, 철학 philosophy, 융합 interdisciplinary, 예술 art.
- 순서는 그 주제에서 배우는 차례다. 도메인 README 의 학습 경로 차례대로 10, 20, 30 을 붙인다. 같은 차례에 나란히 하는 카드는 일의 자리로 나눈다(예: 30, 31, 32). 학습 경로에 없는 카드는 학습 경로 카드 뒤에 차수 순서로 붙인다.
- 실무 도메인에서는 종류가 달라도 같은 순서 번호를 두 번 쓰지 않는다. 구현 영역의 kotlin 주제에서는 code 카드와 fix 카드가 순서 번호를 따로 쓴다(C-kotlin-10 과 F-kotlin-10 이 함께 있다).
- 사이에 카드를 넣을 때는 빈 번호를 쓴다. 다른 카드의 번호는 바꾸지 않는다.
- 여러 카드의 번호를 한꺼번에 다시 매길 때는 `docs/decisions.md` 에 결정을 적는다. 옛 번호와 새 번호의 대응표는 `docs/log/` 에 남긴다.
- 새 도메인은 `backend/<도메인>/README.md` 와 `cards/` 를 만들고 [backend/README.md](backend/README.md) 의 '도메인 지도' 절의 표에 더한다. README 첫 줄의 `# 제목` 이 INDEX.md 의 도메인 이름이 된다.
- 한 줄 아이디어는 도메인 README 의 아이디어 표에 먼저 적고 만들기로 정하면 카드로 옮긴다.
- 다른 문서의 절을 가리킬 때는 번호 대신 `'제목' 절` 꼴로 쓴다. 예: `이 도메인 README 의 '출처' 절`, `[backend/README.md](backend/README.md) 의 '환경' 절`. 절을 더해도 번호가 밀려 엉뚱한 절을 가리키는 일이 없다. 검사기가 그 제목의 절이 있는지와 하나뿐인지 확인한다.
- 카드를 고치면 `kotlin content/ideas/index.main.kts` 로 검사하고 INDEX.md 를 다시 만든다. `--check` 를 붙이면 고치지 않고 검사만 한다. 문제가 있으면 종료 코드 1 로 끝난다.
