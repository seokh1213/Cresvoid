# engine

cresvoid 엔진이다. Gradle Kotlin DSL 프로젝트이고 실행 코드는 Kotlin 표준 라이브러리와 JDK 21 만 쓴다. 테스트는 Kotest 로 쓴다.

## 빌드와 테스트

`engine/` 에서 실행한다. jenv 가 `.java-version` 을 읽어 JDK 21 을 고른다. jenv 가 없으면 명령 앞에 `JAVA_HOME=$(/usr/libexec/java_home -v 21)` 을 붙인다.

| 할 일 | 명령 |
|---|---|
| 테스트 | `./gradlew test` |
| jar 만들기 | `./gradlew jar` |

`./gradlew jar` 는 의존성을 넣은 `build/libs/cresvoid.jar` 를 만든다. `test` 작업은 먼저 jar 를 만든다. 통합 테스트가 `/tmp` 에 시험용 프로젝트를 만들고 그 jar 를 새 JVM 으로 실행하기 때문이다.

실행 명령은 `engine/bin/cresvoid` 다. 엔진 소스가 jar 보다 새로우면 jar 를 다시 만든 뒤 실행한다. PATH 에는 `<cresvoid>/engine/bin` 을 추가한다.

## 끝에서 끝까지 확인

cresvoid 루트에서 `kotlin engine/e2e.main.kts` 를 실행한다. 스크립트는 `content/tracks` 를 임시 디렉터리에 복사한다. 그다음 트랙마다 검증하고 모든 단계를 푼 뒤 진행 기록을 초기화한다. 실패하면 임시 디렉터리를 남긴다.

## 패키지

| 패키지 | 맡는 일 |
|---|---|
| cli | 명령 인터페이스, 옵션 해석, 명령 분배 |
| command | 명령마다 클래스 하나 |
| content | 트랙, 단계, 트랙 종류 규칙, 문서, 문항, 테스트 정의 |
| progress | 진행 기록 모델, 저장소, 초기화 범위 |
| study | 학습 상태, 다음 할 일, 채점, 테스트 관문 |
| runner | 프로세스 실행, 하네스, 판정 |
| validation | 검증 단계와 트랙 종류별 순서 |
| json | JSON 읽기와 쓰기 |
| terminal | 출력, 입력, 색, 글자 폭 |
| project | 루트와 경로 |

패키지 의존은 다음 규칙을 따른다.

- json, terminal, project 는 다른 패키지를 쓰지 않는다.
- content 와 progress 는 json, terminal, project 만 쓴다.
- study 와 runner 는 content, progress, json, terminal, project 만 쓴다.
- validation 은 command 와 cli 를 쓰지 않는다.
- command 는 다른 모든 패키지를 쓸 수 있다.
- cli 는 명령 인터페이스만 정의하고 command 를 쓰지 않는다.

## 효과 경계

부수 효과는 인터페이스 다섯 개 뒤에 둔다. `Main.kt` 가 구현체를 만들어 명령의 생성자로 넘긴다.

| 효과 | 인터페이스 | 구현체 |
|---|---|---|
| 진행 기록 저장 | `ProgressRepository` | `FileProgressRepository` (`practice/progress/`) |
| 프로세스 실행 | `ProcessRunner` | `SystemProcessRunner` |
| 화면 출력 | `Console` | `SystemConsole` |
| 터미널 입력과 문서 보기 | `Terminal` | `SystemTerminal` |
| 현재 시각 | `java.time.Clock` | `Clock.systemDefaultZone()` |

단위 테스트는 `InMemoryProgressRepository`, `RecordingConsole`, 고정 시계로 명령을 시험한다. 새 JVM 을 실행하지 않는다.
