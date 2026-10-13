# 01. 블로킹 호출과 디스패처 고갈

## 개요

[1] 이 단계는 HTTP 서비스에 부하를 보내며 요청 경로 간 간섭을 관찰하는 실험이다. `cresvoid start coroutine-lab`으로 작업 공간을 만들고 코드를 읽은 뒤, 예측 문항을 풀어라. 이어 `cresvoid test coroutine-lab -stage 1`을 실행해 시작 코드의 지표를 확인하라. 그런 다음 느린 블로킹 호출을 격리하고 동시 실행 수를 제한해 다시 검사하라.

## 배경 개념

[2] `Dispatchers.Default`는 계산 작업에 쓰는 공유 디스패처다. 이 실험은 기계의 코어 수와 관계없이 `Default.limitedParallelism(2)` 뷰에서 `/fast` 경로의 작업을 실행한다. 시작 코드의 `/blocking` 경로도 같은 뷰에서 300밀리초 동안 스레드를 막는다. 두 작업이 그 뷰의 실행 자리를 모두 차지하면 `/fast`도 대기한다.

[3] `Dispatchers.IO`는 블로킹 IO 작업을 맡길 수 있는 디스패처다. `IO.limitedParallelism(4)`는 동시에 실행할 수 있는 작업 수를 제한하지만, 중단된 코루틴을 포함한 전체 요청 수의 상한은 보장하지 않는다. 블로킹 호출 자체의 동시 실행 수가 2 이하여야 한다면 `Semaphore(2)`처럼 호출을 감싸는 별도의 허가 관리가 필요하다.

## 서비스와 측정 규칙

[4] 학습자 프로젝트의 `build.sh`는 `build/app.jar`를 만들고 `run.sh`는 서버를 실행한다. 서버는 환경 변수 `PORT`가 가리키는 로컬 포트에서 `/health`, `/fast`, `/blocking`, `/internal/stats`를 제공한다. `/health`는 준비 확인용이다. `/fast`와 `/blocking`은 성공 시 HTTP 200을 반환한다. `/internal/stats`는 블로킹 호출의 시작 수, 완료 수, 현재 실행 수, 관측된 최대 동시 실행 수를 반환한다.

[5] 실험기는 먼저 `/blocking` 요청 12개를 20밀리초 간격으로, `/fast` 요청 20개를 30밀리초 간격으로 예약한다. 응답이 끝나기를 기다리지 않고 예정된 시각에 다음 요청을 보내는 개방형 부하다. 모든 요청이 HTTP 200으로 완료되어야 하며, `/fast` 응답 시간의 p95는 500밀리초 이하여야 한다. 실험기는 가장 느린 값을 p95로 삼지 않고, 오름차순으로 정렬한 20개 중 19번째 값을 쓴다.

[6] 같은 부하에서 `/internal/stats`의 블로킹 호출 시작 수와 완료 수는 각각 12여야 한다. 현재 실행 수는 0이어야 하며 관측된 최대 동시 실행 수는 2 이하여야 한다. `/fast`를 구하기 위해 블로킹 요청을 버리거나 실패시키는 구현은 통과하지 못한다.

## 작업과 검사

[7] 시작 코드의 `BlockingCall.kt`를 고쳐 블로킹 호출을 계산 경로에서 분리하라. 이 실험에서는 `Dispatchers.IO.limitedParallelism(4)`로 실행 자리를 확보하고, 호출을 감싼 `Semaphore(2)`로 실제 블로킹 동시 실행 수를 제한하는 방식을 연습한다. HTTP 서버와 통계 계약은 유지하라.

[8] `cresvoid test coroutine-lab -stage 1`은 실험 지표를 표시하고 [5], [6]의 불변식을 검사한다. 지연 시간은 실제 스케줄링의 영향을 받으므로 이 단계의 p95 기준은 실습 환경에서 여유를 둔 값이다. 매 실행의 개별 수치가 같을 필요는 없다.

공식 API: [Dispatchers.IO](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-i-o.html), [limitedParallelism](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/limited-parallelism.html), [Semaphore](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-semaphore/).
