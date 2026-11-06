# 과부하와 load shedding

[1] 처리 능력을 넘는 요청이 오면 응답이 늦어지고 유용한 처리량(goodput)이 떨어진다. 이 단계에서는 과부하에서도 goodput과 받아들인 요청의 지연을 지켜라. 먼저 시작 코드를 읽고 부하에 따른 실행·대기·만료 지표를 예측해라. 실험으로 예측을 확인하고 `AdmissionController.kt`를 고친 뒤 같은 실험으로 비교해라.

[2] 서비스는 `PORT` 환경 변수로 받은 포트의 `127.0.0.1`에서 HTTP를 제공한다. `GET /health`는 준비되면 200과 `ready`를 반환한다. `POST /work`의 본문은 비어 있으며 `X-Request-Id`는 영문자·숫자·하이픈 1~64자, `X-Deadline-Epoch-Ms`는 Unix epoch 밀리초 정수다. 헤더가 없거나 형식이 틀리면 400과 `invalid`다. 다른 경로·메서드는 404와 `missing`이다.

[3] 작업을 실제로 완료하면 200과 `done:<요청 id>`, 처리하지 않으면 503과 `busy`, `Retry-After: 1`을 반환해라. 거절은 500 오류가 아니다. `handle(deadlineNanos, work)`는 작업을 정확히 한 번 호출해 완료했을 때만 true를 반환하고, 거절하면 작업을 호출하지 않고 false를 반환해야 한다. 받은 허가는 작업이 예외로 끝나거나 대기가 중단되어도 반환해라. 대기 중에는 서비스 잠금을 쥐지 마라.

[4] 작업 하나는 `WorkService.perform`에서 250ms 동안 쉰다. 동시에 실행할 수 있는 작업은 최대 16개다. 따라서 이상적인 처리 능력은 초당 64개지만 실험에서는 실제 처리 능력을 따로 보정한다. 실행 허가를 기다리는 요청은 최대 8개이며 큐가 가득 차면 기다리지 말고 거절해라. 큐를 두지 않고 바로 거절하는 구현도 허용하지만 처리량 기준은 같게 적용한다. 실행 허가를 기다리는 구간은 반드시 `backend.awaitSlot { ... }`로 감싸라. 이 도우미는 관측만 하며 허가 획득이나 큐 길이 제한은 하지 않는다.

[5] HTTP 경계에서 epoch 밀리초로 받은 마감을 최대 ±60초 범위로 제한한 뒤 `System.nanoTime()` 기준 마감으로 바꾼다. 마감이 지난 요청을 새로 받아들이지 말고, 실행 허가를 얻은 직후에도 마감을 다시 확인해라. 마감이 지나면 기다림을 끝내고 거절해라. 작업을 시작한 뒤에는 취소하지 않고 완료한다. `expired_started`는 작업 시작 시 마감이 이미 지난 횟수이며 0이어야 한다. 요청 도착 때의 확인만으로는 큐에서 만료되는 요청을 걸러낼 수 없다.

[6] 실험은 새 프로세스를 띄워 준비를 기다린 뒤 `POST /calibrate`를 호출한다. 이 경로는 제한기를 거치지 않고 작업 16개를 병렬로 실행하며 각 작업을 8번 반복한다. 처리 능력은 완료한 128개를 전체 경과 초로 나눈 값이다. 다른 요청이 없는 상태에서만 보정하고 `POST /reset`으로 카운터를 초기화한다. 이어 사전 확인 시작을 0ms로 두고 정상 요청 16개(마감 1,200ms), 40ms에 요청 8개(마감 100ms), 800ms에 요청 32개(마감 -1,000ms)를 보낸다. 세 묶음의 마감은 모두 사전 확인 시작 시각을 기준으로 한다. 처음 16개는 모두 200이어야 하고 마지막 32개는 모두 503이어야 한다. 중간 8개는 실행 전에 마감이 지났다면 거절해야 한다. 이 구간은 goodput 산정에서 제외한다.

[7] 본 부하는 응답 완료와 관계없이 예정 시각에 보내는 개방형 모델이다. `steady-arrivals`는 초당 180개를 8초간 보낸다. `changing-arrivals`는 2초마다 초당 100개, 200개, 120개, 180개로 바꾼다. 모든 요청의 마감은 예정 도착 시각으로부터 1,200ms 뒤다. 처음 2초는 예열이고 이후 6초에 도착하도록 예정된 요청만 지연과 goodput 산정에 쓴다. 부하 종료 뒤 응답을 모두 모은다. goodput은 이 요청 중 200을 받고 클라이언트 경과 시간이 1,000ms 이하인 개수를 6초로 나눈 값이다. goodput은 같은 실행에서 보정한 처리 능력의 90% 이상이어야 한다.

[8] 받아들인 요청의 p99는 1,000ms 이하여야 한다. 받아들인 요청은 측정 구간의 200 응답이다. p99는 경과 시간을 정렬한 뒤 올림(개수 × 0.99)번째 값이다. 측정 구간에 503이 하나 이상 있어야 하며 그 응답의 p99는 500ms 이하여야 한다. 부하 후 초당 24개를 2초간 보내는 회복 확인에서는 48개 모두 200이며 각각 1,000ms 이하여야 한다. 측정 지연에는 HTTP 왕복과 서버 대기가 포함된다. 클라이언트 타임아웃·연결 오류·200/503 이외 응답·잘못된 본문은 모두 실패다.

[9] `GET /stats`는 `active`, `waiting`, `max_active`, `max_waiting`, `started`, `completed`, `expired_started`를 `이름=정수` 한 줄씩 반환한다. 보정 초기화 이후 모든 구간에서 `max_active ≤ 16`, `max_waiting ≤ 8`, `expired_started = 0`이어야 한다. 마지막에는 active와 waiting이 0이고 started와 completed가 같으며, completed가 전체 200 응답 수와 같아야 한다. 지표는 `capacity_rps`, `goodput_rps`, `accepted_p99_ms`, `rejected_p99_ms`, `peak_active`, `peak_waiting`, `expired_started`, `request_failure_flags`, `final_counter_flags`의 9개다. peak 지표는 각각 `max_active`, `max_waiting`이다. 두 flags 지표는 아래에서 정한 위반 값의 합이며 모두 0이어야 한다.

[9.1] `request_failure_flags`는 잘못된 응답이 있으면 1, 사전 확인 또는 HTTP·handle 계약 검사에 실패하면 2, 회복 확인에 실패하면 4, 본 부하의 측정 구간에 503이 없으면 8을 더한다. 같은 종류의 위반이 여러 번 있어도 해당 값은 한 번만 더한다. 예를 들어 값이 5이면 응답과 회복 확인에 각각 위반이 있다.

[9.2] `final_counter_flags`는 마지막 `active ≠ 0`이면 1, `waiting ≠ 0`이면 2, `started ≠ completed`이면 4, `completed ≠ 초기화 이후 전체 200 응답 수`이면 8을 더한다. 여기서 전체 200 응답 수는 사전 확인·예열·본 부하 측정·회복 확인을 모두 포함한다. 예를 들어 값이 12이면 두 카운터 일치 조건을 모두 어긴 것이다.

[10] `starter/src/main/kotlin/AdmissionController.kt`만 수정해라. 클래스와 `handle` 계약을 유지하고 보조 선언은 같은 파일에 둘 수 있다. HTTP 경계, 고정 작업, 보정, 관측 도우미, 빌드·실행 스크립트는 수정하지 마라. 실행은 JDK 21과 Kotlin 표준 라이브러리만 사용한다. `./build.sh`로 실행 jar를 만들고 `PORT=18080 ./run.sh`로 수동 관찰할 수 있다. 실험은 서비스 시작·준비 확인·부하·종료를 직접 맡으므로 학습자가 서버를 미리 띄울 필요는 없다.

## 관찰 후 힌트

[11] 지표와 시작 코드를 비교한 뒤에도 수정 방법이 떠오르지 않으면 JDK 21 공식 [Semaphore 문서](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html)와 [ThreadPoolExecutor 문서](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)를 참고해라. 허가 획득의 대기·공정성 계약과 큐·거절 정책을 비교하고 이 서비스의 불변식에 맞는 방법을 골라라.
