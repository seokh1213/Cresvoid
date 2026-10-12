---
id: B-coroutines-20
title: 코루틴으로 동시 처리 짜기
kind: build
track: coroutine-patterns
stage: 01-fan-out, 02-limits, 03-channels, 04-flow
status: 완료(2026-10-12)
wave: 3
env: kotlinx-coroutines
prereq: R-coroutines-10
---

# B-coroutines-20 코루틴으로 동시 처리 짜기

- 목표: kotlinx.coroutines 로 실무에서 자주 짜는 동시 처리를 직접 구현한다. 구조적 동시성, 취소, 시간 제한, 동시 실행 상한, Channel, Flow 를 쓰는 법을 손에 익힌다. 내부 원리는 이 트랙 뒤에 B-coroutines-40 에서 실행기를 직접 만들어 확인한다.
- 입출력 모양: 표준 입력의 시나리오 명령으로 가짜 하위 서비스의 지연, 실패, 응답을 정한다. 학습자 코드가 처리한 결과와 사건 기록을 가상 시각과 함께 출력한다. 시작 코드는 시나리오를 읽고 kotlinx-coroutines-test 의 가상 시간(`runTest`)에서 학습자 함수를 실행하는 틀을 준다. 실제 시간을 기다리지 않으므로 결과가 결정적이다.
- 1단계 `01-fan-out`: 상품 상세에 필요한 가격, 재고, 리뷰를 동시에 불러 합친다. `coroutineScope` 와 `async` 로 동시에 부르고 하나가 실패하면 나머지를 취소한다. 리뷰처럼 없어도 되는 호출은 `supervisorScope` 와 기본값으로 감싼다. 호출마다 `withTimeout` 을 걸고 전체 마감 시각을 지킨다.
- 2단계 `02-limits`: 상품 수백 개를 한꺼번에 처리할 때 동시 호출 수를 상한 안에 둔다. 사용자 취소가 진행 중인 호출까지 전파되고 정리 작업은 `withContext(NonCancellable)` 안에서 끝난다. 실패한 호출은 가상 시간의 지수 백오프로 다시 시도한다.
- 3단계 `03-channels`: 수집, 변환, 저장 단계를 Channel 로 잇는 파이프라인을 만든다. 변환 작업자 여러 개가 한 채널에서 일을 나눠 받고 결과를 한 채널로 모은다. 버퍼 크기로 생산 속도를 늦추고 생산이 끝나면 채널을 닫아 소비자 반복이 끝나게 한다. 작업자 하나가 실패하면 파이프라인 전체를 정리한다.
- 4단계 `04-flow`: 가격 변경 이벤트를 Flow 로 처리한다. `map`, `filter`, `scan`, `distinctUntilChanged`, `buffer`, `conflate` 로 흐름을 만들고 현재 가격을 `StateFlow` 로 내보낸다. 늦게 구독한 수집자가 받는 값과 느린 수집자가 건너뛰는 값을 명세대로 맞춘다.
- 난도: 단계마다 2~3시간.
- 함정: `async` 를 만들자마자 `await` 해서 차례로 실행되는 코드, `GlobalScope` 나 새 `CoroutineScope` 로 부모-자식 관계를 끊는 코드, `CancellationException` 을 삼키는 `catch`, `withTimeoutOrNull` 이 null 을 반환해도 정리가 끝나야 하는 자원, 닫지 않은 채널 때문에 끝나지 않는 소비자, 버퍼 없는 채널에서 보내고 받는 순서.
- 검증: build 스킬의 교차 검증. 가상 시간으로 사건 시각까지 비교한다. 참조 풀이와 다른 올바른 구현도 통과하는지 확인한다. 예를 들어 상한을 `Semaphore` 로 구현한 풀이와 작업자 코루틴 N개로 구현한 풀이가 모두 통과해야 한다.
- 출처: Kotlin 문서(Coroutines basics, Composing suspending functions, Coroutine context and dispatchers, Asynchronous Flow, Channels, Shared mutable state and concurrency). kotlinx-coroutines-test API 문서(runTest 와 가상 시간).
- 피할 것: 실험 표시(`@FlowPreview`, `@ExperimentalCoroutinesApi`)가 붙은 API 를 요구하는 것, 실제 시간 대기, 스레드 이름처럼 실행마다 달라지는 출력.
