# content

문제 원본이다. 학습자가 풀어도 이 디렉터리는 바뀌지 않는다. 다만 `cresvoid test` 가 참조 풀이를 빌드하면 `hidden/solution/build/` 가 생긴다. 엔진은 [FORMAT.md](FORMAT.md) 에 적힌 형식만 읽는다.

| 경로 | 내용 |
|---|---|
| [FORMAT.md](FORMAT.md) | 엔진과 컨텐츠 사이의 형식 계약 |
| `tracks/` | 트랙 컨텐츠. 트랙마다 track.json 과 단계 디렉터리가 있다 |
| [ideas/](ideas/README.md) | 역량 지도, 단계 카드, 카드 색인(INDEX.md). 앞으로 만들 단계를 여기서 고른다 |
| [skills/](skills/README.md) | 컨텐츠를 만드는 스킬과 워크플로우 |
| `templates/` | Kotlin 프로젝트 공통 파일의 원본(`kotlin/`)과 복사 스크립트(`sync.main.kts`) |

## 트랙

| 트랙 | 종류 | 단계 수 |
|---|---|---|
| `search-engine` | build | 6 |
| `agent-runtime` | build | 5 |
| `mini-dfs` | build | 3 |
| `es-cluster` | build | 3 |
| `mini-olap` | build | 3 |
| `dataflow` | build | 4 |
| `rate-limiter` | build | 3 |
| `mvcc` | build | 3 |
| `raft` | build | 3 |
| `table-format` | build | 3 |
| `mini-log` | build | 4 |
| `metrics` | build | 3 |
| `coroutine-patterns` | build | 4 |
| `reading` | reading | 3 |
| `tech-reading` | reading | 12 |
| `code-reading` | code | 3 |
| `bug-fix` | fix | 2 |

## 채점용 파일

단계의 `hidden/` 에는 숨은 테스트(`tests.json`), 입력 생성기, 문항 정답(`quiz.json`), 참조 풀이(`solution/`)가 있다. 학습자는 `hidden/` 을 열지 않는다. 참조 풀이는 `cresvoid solution` 으로 본다.

## 컨텐츠 만들기

[skills/cresvoid-content](skills/cresvoid-content/SKILL.md) 의 절차를 따른다. 카드 고르기부터 확인과 기록까지 이 절차에 있다.
