# Kotlin 코루틴

Kotlin 코루틴의 실행 원리, 구조적 동시성, 취소, 예외 전파, 디스패처, Flow 와 Channel 을 익힌다. 코루틴 코드의 실행 순서와 실패 경로를 예측하고 취소가 전파되지 않거나 스레드가 고갈되는 버그를 찾는 힘을 기른다. 공식 라이브러리 kotlinx.coroutines 로 먼저 써 보고 표준 라이브러리의 기본 요소로 실행기를 직접 만들어 내부 원리를 확인한다.

## 1. 개념 지도

### 1.1 실행 원리

- `suspend` 함수는 컴파일러가 이어 할 일(Continuation)을 마지막 인자로 받는 함수로 바꾸고 중단 지점마다 상태를 나눈 상태 기계로 만든다. 중단 지점에서 스레드를 놓고 재개될 때 Continuation 으로 이어 간다.
- 코루틴의 저수준 기본 요소(`kotlin.coroutines` 의 `Continuation`, `createCoroutine`, `startCoroutine`, `suspendCoroutine`)는 표준 라이브러리에 있다. `launch`, `async`, 디스패처, Flow 는 kotlinx.coroutines 라이브러리에 있다.

### 1.2 구조적 동시성과 취소

- 코루틴은 스코프 안에서 시작되고 부모 Job 아래 자식 Job 으로 연결된다. 부모는 자식이 모두 끝나야 끝난다.
- 취소는 협력적이다. kotlinx.coroutines 의 중단 함수는 모두 취소를 확인하고 취소됐으면 CancellationException 을 던진다. 중단하지 않고 계산만 하는 반복문은 `isActive`, `ensureActive()`, `yield()` 로 직접 확인해야 멈춘다.
- 취소된 뒤 정리 작업에서 중단 함수를 불러야 하면 `withContext(NonCancellable)` 로 감싼다.
- `catch (e: Exception)` 나 `runCatching` 이 CancellationException 까지 삼키면 취소가 전파되지 않는다. `catch` 했으면 다시 던진다.
- `withTimeout` 은 시간이 지나면 TimeoutCancellationException 을 던지고 `withTimeoutOrNull` 은 null 을 반환한다.

### 1.3 예외 전파

- `launch` 의 예외는 잡히지 않은 예외로 전파된다. `async` 의 예외는 `await()` 할 때 드러난다.
- CoroutineExceptionHandler 는 최상위(루트) 코루틴의 잡히지 않은 예외에만 불린다. 자식 코루틴은 예외를 부모에게 넘기므로 자식에 설치한 처리기는 쓰이지 않는다.
- CancellationException 은 모든 처리기가 무시한다. 다른 예외로 실패한 코루틴은 부모와 형제를 취소한다.
- SupervisorJob 과 `supervisorScope` 에서는 자식의 실패가 감독자와 형제를 취소하지 않는다. 감독자가 취소되면 자식은 모두 취소된다. 자식은 자기 예외를 직접 처리해야 한다.

### 1.4 디스패처

- `Dispatchers.Default` 의 최대 스레드 수는 CPU 코어 수이고 최소 2다. JVM 에서는 공유 스레드 풀을 쓴다.
- `Dispatchers.IO` 의 기본 병렬도는 64 와 코어 수 가운데 큰 값이다(`kotlinx.coroutines.io.parallelism`). `limitedParallelism()` 으로 만든 IO 의 뷰는 IO 의 한도에 묶이지 않는다. IO 는 Default 와 스레드를 공유하므로 Default 에서 `withContext(Dispatchers.IO)` 로 옮겨도 스레드가 바뀌지 않을 수 있다.
- Default 에서 블로킹 호출을 하면 코어 수만큼의 스레드가 막혀 다른 코루틴이 모두 기다린다.

### 1.5 Flow, Channel, Mutex

- Flow 는 콜드 스트림이다. 수집할 때마다 생산 코드가 다시 실행된다.
- StateFlow 는 값을 합친다. 느린 수집자는 중간 값을 건너뛰고 마지막 값만 받는다. 새 값이 이전 값과 `equals` 로 같으면 내보내지 않는다. 그래서 내용을 고친 같은 가변 객체를 다시 대입하면 수집자가 바뀐 것을 모른다. StateFlow 는 끝나지 않아 `collect` 가 반환되지 않는다.
- SharedFlow 는 다시 보낼 개수(replay), 버퍼, 넘칠 때의 동작을 정한다. StateFlow 는 replay 1 과 DROP_OLDEST 와 distinctUntilChanged 를 합친 SharedFlow 와 같다.
- Mutex 는 재진입하지 않는다. 잠금을 가진 코루틴이 다시 `lock` 하면 스스로 멈춘다.

## 2. 학습 경로

1. R-coroutines-10 기술 지문으로 상태 기계, 구조적 동시성, 취소, 예외 전파를 익힌다.
2. B-coroutines-20 `coroutine-patterns` 트랙에서 kotlinx.coroutines 로 동시 호출, 동시 실행 상한, Channel 파이프라인, Flow 처리를 직접 짠다.
3. C-coroutines-30·C-coroutines-32 코드 읽기와 F-coroutines-31 버그 수정으로 남이 짠 코루틴 코드의 실행 순서와 함정을 만난다.
4. B-coroutines-35 `coroutine-patterns` 05·06 단계에서 묶음 저장과 요청 합치기를 실험 표시 없는 API 로 직접 설계한다.
5. B-coroutines-40 `coroutine-runtime` 01~08단계에서 표준 라이브러리의 기본 요소로 작은 실행기, pull 시퀀스와 수동 상태 기계를 만들어 앞에서 쓴 의미론을 안쪽부터 확인한다.
6. L-coroutines-50 `coroutine-lab` 실험에서 블로킹 호출이 빠른 요청을 지연시키는 과정을 측정하고 고친다.

## 3. 환경

- R-coroutines-10, B-coroutines-40 은 추가 도구가 필요 없다.
- B-coroutines-20, C-coroutines-30, F-coroutines-31, C-coroutines-32, B-coroutines-35, L-coroutines-50 은 kotlinx-coroutines-core 를 쓰고 가상 시간이 필요한 카드는 kotlinx-coroutines-test 도 쓴다(`env: kotlinx-coroutines`). 사용자가 두 의존성을 허용했다(D16).
- 버전은 1.11.0 으로 고정했다. jar 원본은 `content/templates/kotlin/lib/` 에 있고, kotlinx.coroutines 를 쓰는 프로젝트는 `lib/` 를 두면 sync 가 jar 를 복사한다(D21).
- 1.11.0 에서 실험 표시가 붙은 API 는 학습자에게 요구하지 않는다. `chunked`, `flatMapMerge`, `flatMapConcat`, `flatMapLatest`, `mapLatest`, `produce`, `select` 의 `onTimeout` 은 `@ExperimentalCoroutinesApi` 다. `debounce`, `sample`, Flow 의 `timeout` 은 `@FlowPreview`, `actor`, `ticker` 는 `@ObsoleteCoroutinesApi`, `GlobalScope` 는 `@DelicateCoroutinesApi` 다. `select` 와 `onReceive`, `channelFlow`, `callbackFlow`, `shareIn`, `stateIn`, `merge`, `Mutex`, `Semaphore`, `CompletableDeferred`, `SupervisorJob`, `withTimeoutOrNull` 에는 표시가 없다. 2026-10-12 에 1.11.0 jar 로 컴파일해 경고로 확인했다.
- 가상 스레드와의 비교는 JDK 21 만으로 할 수 있다.

## 4. 아이디어

| 아이디어 | 종류 | 메모 |
|---|---|---|
| Channel 과 생산자-소비자 | code | 랑데부 채널, 버퍼, 닫기와 반복의 끝 |
| 가상 스레드와 코루틴 비교 | lab | 같은 블로킹 작업을 두 방식으로 실행한 처리량과 스레드 수 |
| runTest 와 가상 시간 | code | 테스트에서 delay 가 건너뛰어지는 방식 |
| Flow 와 백프레셔 | lab | buffer, conflate, collectLatest 의 차이 |
| 키별 순서를 지키는 병렬 처리와 종료 | build | B-coroutines-35 에서 제외한 7단계 안. 상품별 순서, 작업자 N개, 종료 마감과 처리하지 못한 이벤트 보고. 작업자 풀과 정리 작업이 coroutine-patterns 2·3단계와 겹친다 |

## 5. 출처

- Kotlin 문서, Cancellation and timeouts: https://kotlinlang.org/docs/cancellation-and-timeouts.html
- Kotlin 문서, Coroutine exceptions handling: https://kotlinlang.org/docs/exception-handling.html
- kotlinx.coroutines API, Dispatchers.Default·Dispatchers.IO: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/
- kotlinx.coroutines API, StateFlow: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/
- kotlinx.coroutines API, Mutex: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/
- Kotlin 코루틴 설계 문서(KEEP, Kotlin Coroutines): https://github.com/Kotlin/KEEP/blob/master/proposals/coroutines.md
- kotlinx-coroutines-test API: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/
- 사용자가 추천한 영상: 단편 "코루틴 launch를 직접 만들어? #1"(https://youtu.be/qr8tfjV_U48), 재생목록 "코틀린 코루틴 #1~#8"(https://www.youtube.com/playlist?list=PLBNdLLaRx_rLY7dJSA8Hgeet-PeFb9MUF). 2026-10-12 에 자막으로 확인했고 결과는 '실행기 구현 영상 조사' 절에 있다.

## 6. 실행기 구현 영상 조사

B-coroutines-40(`coroutine-runtime`) 제작에 앞서 사용자가 추천한 영상을 2026-10-12 에 확인했다. 아래 '당시 네 단계 기획과 대조'와 '당시 제안과 반영 결과' 절은 네 단계 카드에 대한 조사와 제안을 기록한 것이다. 이후 실제 제작 범위는 여덟 단계로 늘었으며 [코루틴 현황](../../../../docs/coroutines-roadmap.md)에 정리했다. 영상은 주제만 참고하고 문장과 코드는 옮기지 않는다(D15).

### 6.1 확인한 영상

| 영상 | 올린 날 | 길이 | 내용 |
|---|---|---|---|
| [코루틴 launch를 직접 만들어? #1](https://youtu.be/qr8tfjV_U48) | 2025-07-30 | 10분 23초 | launch 를 직접 만드는 강의의 1편 |
| [코틀린 코루틴 #1~#8 재생목록](https://www.youtube.com/playlist?list=PLBNdLLaRx_rLY7dJSA8Hgeet-PeFb9MUF) | #1 은 2024-04-12 | 편당 67~99분, 모두 약 11시간 | 코루틴 책 한 권을 장 순서대로 설명하는 스터디 |

- 두 영상 모두 뉴런데브 채널이다.
- 시리즈는 직접 구현하는 강의가 아니다. 편 제목에 장 범위가 있고 진행자가 책의 쪽수를 들며 설명한다. 구현 강의는 단편이고 이번에 확인한 것은 1편뿐이다.
- 재생목록 순서와 편 번호가 다르다. #5(16장)가 재생목록의 8번째다. 편 제목에 17장은 없다.

| 편 | 장 | 길이 |
|---|---|---|
| #1 | 1~5장 | 1:37:30 |
| #2 | 6~8장 | 1:29:31 |
| #3 | 9~11장 | 1:28:03 |
| #4 | 12~15장 | 1:38:45 |
| #5 | 16장 | 1:07:05 |
| #6 | 18~21장 | 1:22:11 |
| #7 | 22~25장 | 1:14:44 |
| #8 | 26장~끝 | 1:24:56 |

### 6.2 확인 방법

- youtube-transcript-api 1.2.4 로 아홉 편의 한국어 자동 자막을 받았다. 자막 파일은 프로젝트에 두지 않았다.
- 단편은 자막을 전부 읽었다.
- 시리즈는 편마다 핵심어가 나온 횟수를 세고 주제를 가리는 데 필요한 핵심어 주변만 읽었다. 약 11시간을 처음부터 끝까지 읽지 않았으므로 아래 표에 없는 주제가 더 있을 수 있다.
- 자동 자막이라 영어 용어가 소리 나는 대로 적혀 있다. 핵심어는 한글 표기와 영어 표기를 함께 셌다. 'virtual' 은 가상 스레드를 말하는 곳에서도 나온다. 그래서 핵심어마다 주변을 읽어 뜻을 확인했다.

### 6.3 단편의 흐름

1. 목표는 일반 함수에서 suspend 함수를 실행하는 진입점을 만드는 것이다. 모든 코드를 suspend 로 만들 수는 없으므로 어딘가에 진입점이 있어야 한다.
2. 컴파일러가 suspend 함수에 continuation 매개변수를 더한 꼴로 바꾼다는 설명에서 시작한다. 영상은 continuation 이 첫 번째 인자라고 말하지만 Kotlin 은 마지막 매개변수로 더한다. 실제로는 함수보다 상태를 가진 객체에 가깝게 바뀐다고 설명한다.
3. `startCoroutine` 은 코루틴을 만들고 가로채기(intercepted)를 거쳐 continuation 컨텍스트의 디스패처로 보낸 뒤 첫 재개를 한다. 디스패처는 작업 큐에 등록하고 바로 돌아온다.
4. 본문이 끝나면 넘겨 준 completion continuation 의 `resumeWith` 가 불린다. 중단 지점마다 상태 기계가 다음 상태로 넘어간다.
5. `startCoroutine` 만으로는 코루틴을 멈출 수도 끝났는지 알 수도 없다. 호출한 쪽은 기다리지 않으므로 코루틴의 출력이 나오기 전에 프로그램이 끝날 수 있다.
6. 기다리는 방법으로 `Thread.sleep` 과 `runBlocking` 을 견준다. `runBlocking` 은 호출한 스레드를 막는다. 서버라면 그동안 요청을 받지 못한다.
7. `CoroutineScope.launch` 가 돌려주는 Job 의 `join` 은 suspend 함수라서 일반 함수에서는 기다릴 수 없다. Job 과 기다리는 장치를 직접 만들어야 한다는 데서 1편이 끝난다.

### 6.4 시리즈 편별 주제

핵심어 횟수는 자동 자막에서 그 말이 나온 횟수다. 주제를 가늠하는 지표로만 쓴다.

| 편 | 주로 다루는 주제 | 핵심어 횟수 | B-coroutines-40 과의 관계 |
|---|---|---|---|
| #1 | 중단 원리, continuation, 가로채기와 디스패처, 상태 기계와 레이블, 시퀀스 | continuation 159, 디스패처 108, 컨텍스트 65, 가로채기 20, 상태 기계·레이블 15, 시퀀스 15 | 1단계의 바탕 |
| #2 | 코루틴 빌더, 컨텍스트, 스코프, Job, 지연 시작, 표준 라이브러리만으로도 코루틴을 실행할 수 있고 kotlinx.coroutines 는 편의 함수라는 설명 | 컨텍스트 136, 스코프 79, continuation 77, Job 31, join·await 30, 취소 27 | 1~2단계 |
| #3 | 취소(invokeOnCompletion, yield, ensureActive), 예외, 슈퍼바이저, 스코프 함수 | 취소 90, 스코프 86, Job 44, 예외 34, join·await 21, 슈퍼바이저 17 | 3~4단계 |
| #4 | 디스패처, limitedParallelism, 가상 스레드와의 비교, 공유 상태와 Mutex·Semaphore | 디스패처 63, 컨텍스트 63, Mutex·Semaphore 34, 테스트 3(끝부분) | Mutex 만 관련 |
| #5 | Channel, 버퍼와 랑데부, produce 가 받은 블록을 코루틴으로 시작하는 구조 | Channel 188, 스코프 16 | 확장된 05단계에 반영 |
| #6 | hot·cold 데이터 소스, Flow 소개, Flow 를 fun interface 로 직접 만드는 설명, Flow 만들기 | Flow 156, Channel 54, Job 33 | 확장된 06단계에 반영 |
| #7 | Flow 수명 주기와 처리, SharedFlow·StateFlow, Flow 테스트 | Flow 116 | C-coroutines-32, B-coroutines-20 범위 |
| #8 | 실전 사례와 권장 방식 | Flow 17, 스코프 13 | 관련 적음 |

### 6.5 당시 네 단계 기획과 대조

- B-coroutines-40 카드의 네 단계(01-event-loop, 02-structured, 03-cancellation, 04-exceptions)는 단편이 가는 방향과 맞는다. 단편은 진입점을 만든 다음 Job 과 기다리기로 넘어간다.
- 시리즈 #2·#3 의 범위(빌더, Job, 취소, 예외, 스코프 함수)와도 겹친다.
- 영상에 나오지만 네 단계에 없는 것은 다음과 같다.
  - 컨텍스트와 가로채기: 컨텍스트 원소가 자식에게 물려지는 방식, 가로채기를 거쳐 디스패처 큐로 가는 흐름(#1, #2, 단편)
  - 지연 시작과 Job 상태(#2)
  - yield 와 완료 처리기 invokeOnCompletion(#3)
  - Mutex 와 Channel(#4, #5)
  - cold Flow 직접 만들기(#6)
  - 시퀀스 생성기(#1)

### 6.6 당시 제안과 반영 결과

이 제안을 검토한 뒤 05·06을 더해 여섯 단계로 정했다. 시퀀스 생성기는 선택안으로 남겼다.

1. 기존 단계에 더할 것
   - 01-event-loop: 가로채기를 거쳐 작업 큐에 넣는 디스패치. 바로 돌아오는 시작과 루프를 끝까지 돌리는 진입점의 차이.
   - 02-structured: 자식이 부모의 컨텍스트(이름, 예외 처리기)를 물려받고 일부를 덮어쓰는 규칙, 지연 시작(start 나 join 을 부를 때 시작), Job 상태를 출력하는 명령.
   - 03-cancellation: yield(같은 시각에 준비된 다른 코루틴에게 차례를 넘김), 완료 처리기가 완료·취소·실패 때 불리는 시점과 순서.
2. 새 단계 후보
   - 05-channels: 랑데부 채널과 버퍼 채널, send 와 receive 의 중단과 재개, close 뒤의 동작, Mutex 의 대기 순서.
   - 06-flow: collector 를 받는 cold Flow, flow 빌더, map·filter·take 연산자, take 가 위쪽 흐름을 멈추는 방식, 수집할 때마다 처음부터 실행되는 성질.
3. 선택: 맨 앞에 시퀀스 생성기 단계를 둔다. 디스패처 없이 중단과 재개만 다루므로 지금 가장 어려운 1단계로 들어가는 계단이 된다.
4. 넣지 않을 것
   - 실제 스레드를 쓰는 디스패처와 limitedParallelism: 단일 스레드 가상 시계 설계와 맞지 않는다.
   - select: B-coroutines-40 의 목표(구조적 동시성, 취소, 예외의 의미론)에서 멀다.
   - SharedFlow·StateFlow 구현: C-coroutines-32·B-coroutines-20 이 쓰는 법과 의미를 다루고 구현 부담이 크다.
5. 규모: 모두 넣으면 네 단계가 최대 일곱 단계가 된다. 늘어난 단계마다 숙련 개발자 기준 2~3시간 분량이다. 지금까지 build 워크플로우 실측은 네 단계 트랙 하나에 6~12시간이었으므로 만드는 시간도 그만큼 는다.
