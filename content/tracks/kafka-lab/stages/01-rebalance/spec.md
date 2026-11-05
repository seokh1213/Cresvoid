# 느린 처리와 재조정 폭풍

[1] 메시지 처리 서비스가 Kafka 토픽을 읽고 작업을 완료하는 과정을 관찰하라. 이 단계는 느린 작업에 맞는 컨슈머 설정을 진단하고 중단 시 완료 기록과 커밋 위치가 어떻게 달라지는지 확인하는 입문 실험이다. 먼저 시작 코드로 실험해 같은 ID의 처리 시도 수, 파티션 할당 횟수, 커밋 실패를 기록하라. 그다음 필요한 설정이나 처리·커밋 순서를 고쳐 모든 불변식을 만족시켜라. 설정만 바꾼 풀이도 허용하며 수정 줄 수나 특정 구조를 요구하지 않는다. 실험은 단일 브로커, 단일 파티션, 하나의 컨슈머 그룹을 사용한다. 복제 장애나 정확히 한 번 처리를 다루는 단계는 아니다.

[2] 컨슈머는 `subscribe`로 그룹에 참여한다. `poll` 사이 간격이 `max.poll.interval.ms`를 넘으면 classic 그룹의 동적 멤버는 그룹을 떠나고 다시 할당받을 수 있다. 커밋 오프셋은 같은 그룹의 새 컨슈머가 다음에 읽을 레코드의 위치다. 완료 기록만 남기고 커밋하지 않은 레코드는 다시 전달될 수 있다. `max.poll.records`는 poll 한 번에 애플리케이션이 받는 레코드 수를 제한하며 브로커의 fetch 크기를 제한하지는 않는다. 처리 묶음의 소요 시간과 poll 간격 한도를 함께 비교하라. 백그라운드 heartbeat가 살아 있어도 poll 지연 검사는 사라지지 않는다.

[3] 공개 서비스 계약은 다음과 같다. 서버는 `PORT`로 받은 포트를 사용하고 Docker 밖에서는 루프백으로만 공개한다. 컨테이너 안에서는 포트 전달을 위해 모든 인터페이스에 바인딩한다. `KAFKA_BOOTSTRAP`은 브로커 주소다. `GET /health`는 토픽과 프로듀서 준비 후 200과 `ready`를 반환한다. `POST /messages`는 본문으로 0부터 11까지의 십진수 ID 하나를 받아 Kafka에 기록한 뒤 202와 그 ID를 반환한다. 범위 밖 입력은 400, 중복 ID는 409다. 메시지는 `work-queue`의 파티션 0에 저장하며 그룹 ID는 `lab-consumer`다. 프로듀서는 `acks=all`과 멱등 전송을 사용한다.

[4] `POST /start`는 소비를 시작하고 200을 반환한다. `POST /restart`는 현재 컨슈머를 중단하고 동일한 그룹의 새 컨슈머를 시작한 뒤 200을 반환한다. 중단 시 진행 중인 700ms 작업은 취소되며 완료로 세지 않는다. 작업별 관찰 기록은 HTTP 프로세스 안에 남는다. `POST /stop`은 소비를 멈추고 200을 반환한다. 각 명령의 본문은 비어 있다. 컨슈머 교체는 프로세스 장애를 흉내 내기 위한 통제된 중단이며, 처리하지 않은 레코드까지 커밋해서는 안 된다. 정상 완료한 작업은 후속 커밋 실패와 무관하게 완료 기록에 남는다.

[5] 실험은 빈 토픽에 12개 ID를 초당 30건의 개방형 부하로 적재한다. 도착 예정 시각은 0ms, 33ms, 66ms, …, 366ms이며 앞선 응답이 느려도 다음 요청은 보낸다. 모든 전송을 확인한 뒤 소비를 시작하고, 시작 요청이 끝난 시점으로부터 3500ms 뒤 컨슈머 교체 요청을 보낸다. 메시지 한 건의 실제 작업 시간은 700ms다. 모든 ID가 완료되면 1초 더 관찰하고 소비를 멈춘다. 미완료 ID가 있으면 교체 요청 종료 뒤 최대 24.5초를 더 관찰하고 멈춘다. 교체 요청 자체의 지연은 이 대기 시간에서 차감하지 않는다. 매 실험은 새 브로커와 토픽·그룹을 사용한다.

[6] `GET /stats`는 200과 줄 단위 텍스트를 반환한다. 앞부분은 `accepted=N`, `assignments=N`, `commitFailures=N`, `fatalErrors=N`이고 나머지는 `ID,시도수,완료여부,최초완료지연ms` 형식이다. 완료여부는 0 또는 1이며 미완료 지연은 0이다. 시도수는 실제 작업 진입 직전에 증가하므로 취소된 시도도 포함한다. 완료 수는 완료여부가 1인 서로 다른 ID 수다. `assignments`는 비어 있지 않은 `onPartitionsAssigned` 콜백 수이며 최초 할당과 계획된 교체 뒤 할당도 포함한다. 이 지표는 브로커의 모든 내부 재조정 단계 수가 아니라 이 컨슈머가 받은 할당 횟수다. 커밋 실패는 `CommitFailedException` 횟수이며 정상 중단 외 소비 스레드 오류는 `fatalErrors`다.

[7] 12개 ID가 모두 처리 완료되어야 한다. `accepted=12`, `completed=12`, ID별 최대 시도수 `max_attempts≤2`, `assignments=2`, `commit_failures=0`, `fatal_errors=0`, `http_errors=0`을 모두 만족시켜라. `http_errors`는 적재 응답이 202가 아니거나 전송 오류가 난 요청 수다. `max_latency_ms`는 Kafka 레코드 타임스탬프부터 최초 작업 완료까지의 최댓값이며 22000ms 이하여야 한다. 정상 직렬 처리의 총 작업량 8400ms보다 여유를 두되 반복 처리로 생기는 지연을 무제한 허용하지 않는 기준이다. 실험 종료 후 브로커에서 조회한 파티션의 끝 오프셋과 `committed_offset`은 모두 12여야 한다. 완료하지 않은 ID가 있으면 지연 수치가 작아도 실패다.

[8] 수정 범위는 `src/main/kotlin/lab/ConsumerLoop.kt` 안의 컨슈머 설정과 소비·중단·커밋 구조다. 필요한 보조 타입도 이 파일 안에 추가하라. `group.protocol=classic`, 그룹 ID, 토픽, 단일 컨슈머, `subscribe` 방식과 수동 커밋은 유지하라. `assign`으로 그룹 참여를 건너뛰거나, 오프셋을 임의로 이동하거나, 처리 기록을 조작하지 마라. `Main.kt`, `Work.kt`, `.lab-libs`, 빌드·실행 파일과 실험기는 고치지 마라. 700ms 작업을 줄이거나 Kafka를 우회하지 마라. 중복을 숨기기 위해 작업 호출을 생략해서도 안 된다.

[9] Docker Desktop을 실행하고 시작 코드의 `build.sh`로 빌드하라. 엔진의 실험 명령은 브로커와 HTTP 서비스를 준비한 뒤 부하를 보내고 자원을 정리한다. 우선 관찰값으로 poll 간격 초과, 재할당, 커밋 실패, 재전달의 연결을 설명하라. 이후 설정 하나를 바꿀 때마다 같은 부하로 다시 확인하라. 커밋만 앞당겨 중복을 줄이면 교체 도중 미완료 레코드를 건너뛸 수 있다. `max_attempts`가 작다는 사실만으로 통과를 판단하지 마라.

[10] 공식 근거는 [Kafka Consumer Configs](https://kafka.apache.org/41/configuration/consumer-configs/)의 `max.poll.interval.ms`, `max.poll.records`, `enable.auto.commit`, `group.protocol`과 [Consumer Rebalance Protocol](https://kafka.apache.org/41/operations/consumer-rebalance-protocol/)이다. [KafkaConsumer API](https://kafka.apache.org/41/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html)의 Detecting Consumer Failures, Manual Offset Control, Multi-threaded Processing 절에서 poll, 완료 뒤 커밋, 스레드 안전성·wakeup 계약을 확인하라. 문서는 Kafka 4.1 계열이고 실행 환경은 고정 이미지의 Kafka 클라이언트·브로커 4.4.0이다. 기본값에 의존하지 않고 이 단계에서 쓰는 설정을 코드에 명시한다.
