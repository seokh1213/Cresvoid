# acks와 최소 동기 복제본

[1] 이 단계는 observe 실험이다. 주문 이벤트를 받는 HTTP 서비스로 Kafka의 성공 응답과 장애 뒤 남은 레코드를 비교하라. 먼저 네 설정 조합의 결과를 예측하고 시작 코드로 관찰하라. 시작 코드가 공개 계약을 어기는 지점을 찾은 뒤 설정 전달과 관측 코드를 고쳐 같은 실험을 다시 실행하라. 내구성을 높이려고 모든 조합을 같은 설정으로 바꾸지 마라.

[2] 실행 환경은 JDK 21, 기존 Kotlin 실행 템플릿과 실행 이미지의 kafka 라이브러리 묶음이다. 프로젝트 루트의 `.lab-libs`는 `kafka`다. `build.sh`는 `build/app.jar`를 만들고 `run.sh`는 `PORT`의 HTTP 포트로 실행한다. 호스트에 공개하는 포트는 루프백이다. 실험기는 앱과 브로커를 같은 Docker 네트워크에 띄우며 `KAFKA_BOOTSTRAP=kafka1:9092,kafka2:9092,kafka3:9092`를 전달한다. `GET /health`는 HTTP 200과 `ready`를 반환한다.

[2.1] Docker Desktop을 켜고 저장소 루트에서 `engine/bin/cresvoid start kafka-lab -stage 2`로 작업 공간을 준비하라. `engine/bin/cresvoid quiz kafka-lab -stage 2`의 예측 문항을 모두 마친 뒤 `engine/bin/cresvoid test kafka-lab -stage 2`로 실험하라. 이 명령이 빌드, 앱·브로커 실행, [7]의 장애 조작과 읽기, 지표 집계, 서비스 종료를 모두 맡는다. 브로커를 수동으로 조작할 필요는 없다. 기본 작업 공간은 `practice/kafka-lab/02-durability/`이며, 별도 경로를 사용했다면 test에 `-dir <작업 공간>`을 붙여라. `run.sh`만으로는 클러스터가 준비되지 않는다.

[3] 브로커는 1, 2, 3번이며 별도 단일 KRaft 컨트롤러가 메타데이터를 유지한다. 토픽은 모두 파티션 하나다. `quorum`은 복제본 [1,2,3], `acks=all`, `min.insync.replicas=2`다. `leader`는 복제본 [1,2,3], `acks=1`, `min.insync.replicas=2`다. `flexible`은 복제본 [1,2,3], `acks=all`, `min.insync.replicas=1`이다. `single`은 복제본 [1], `acks=all`, `min.insync.replicas=1`이다. 토픽 이름은 각각 `durability-<조합 이름>`이다. 최초 리더는 1번이며 토픽은 비어 있다. 모든 토픽의 `unclean.leader.election.enable=true`는 오래된 복제본 승격을 관찰하기 위한 실험 조건이다. 실제 운영에서 그대로 쓰라는 권고로 읽지 마라.

[4] `POST /setup`은 [3]의 토픽과 프로듀서를 만들고 HTTP 200과 `configured`를 반환한다. `GET /topology`는 조합 순서 quorum, leader, flexible, single로 `이름 리더번호 ISR번호목록`을 한 줄씩 반환한다. ISR 번호는 오름차순 쉼표 구분이며 없으면 `-`다. 리더가 없으면 리더번호는 -1이다. 토픽 설정은 실험 중 바꾸지 마라. 프로듀서는 멱등성을 끄고 `retries=0`, `linger.ms=0`, `request.timeout.ms=5000`, `delivery.timeout.ms=12000`, `max.block.ms=10000`으로 설정한다. 자동 재시도와 중복 전송을 없애고 브로커 응답을 직접 관찰하기 위한 조건이다.

[5] `POST /send/<조합>/<id>`는 본문 없이 해당 토픽의 파티션 0에 키와 값 모두 id인 레코드를 한 번 전송한다. id는 `b0`~`b3` 또는 `d0`~`d3`다. 전송 future의 성공을 확인한 뒤 HTTP 200과 `ACK <id>`를 반환하라. 원인 예외가 `NotEnoughReplicasException` 또는 `NotEnoughReplicasAfterAppendException`이면 HTTP 409와 `NOT_ENOUGH_REPLICAS <id>`를 반환하라. 다른 전송 오류는 HTTP 500과 `ERROR <id>`로 구분하라. 단순 enqueue를 ACK로 세거나 전송 실패를 성공으로 숨기지 마라. 서로 다른 요청의 응답을 공유하지 마라.

[6] `GET /records/<조합>`은 실제 Kafka 로그를 처음부터 읽어 보이는 값들을 문자열 오름차순으로 한 줄씩 반환한다. 빈 로그의 본문은 비어 있다. 자동 커밋 없이 직접 파티션을 할당하고 시작 오프셋부터 현재 끝 오프셋까지 읽어라. 중지된 주소의 연결 대기로 관찰이 흔들리지 않도록 클러스터 메타데이터의 살아 있는 브로커로 읽기 클라이언트를 연결하라. 단일 소켓 연결 설정 대기는 처음 1000ms, 최대 2000ms다. 전송 기록이나 예상값으로 읽기 결과를 대신하지 마라. `GET /receipts`는 요청마다 남긴 `조합 id ACK`, `조합 id NOT_ENOUGH_REPLICAS`, `조합 id ERROR` 행을 조합과 id 순서로 반환한다. 전송이 끝난 결과를 중복 없이 기록하라. 정상 경로는 HTTP 200이다. 정의하지 않은 경로는 404, 잘못된 조합이나 id는 400이다.

[7] 자동 실험의 관찰 순서는 다음과 같다. setup 뒤 모든 복제본이 ISR에 있고 리더가 1번인지 확인한다. 각 조합에 b0~b3를 보내 총 16건의 성공을 확인하고 모든 토픽에서 b0~b3가 읽힐 때까지 기다린다. 2번과 3번 브로커를 멈추고 모든 토픽의 ISR이 [1]이 될 때까지 기다린다. 각 조합에 d0~d3를 보내 결과를 모은다. 다음으로 1번을 강제 종료하고 2번과 3번만 다시 시작한다. 복제 수 3인 토픽의 리더와 ISR이 [2,3] 안에서 회복되면 그 세 토픽을 읽는다. 이때 single에는 리더가 없어야 한다. 마지막으로 1번을 다시 시작하고 모든 ISR이 회복되면 single을 읽는다. 장애 조작 중 디스크나 로그 파일은 지우지 않으며 실험 종료 때 컨테이너와 볼륨을 정리한다. 관찰의 각 구간은 상태 확인 뒤 시작하며 장애 전후 경계에 걸친 요청은 없다.

[8] 각 쓰기 구간은 조합마다 초당 4건씩 1초간, 전체 초당 16건의 개방형 부하다. 도착 시각은 0, 250, 500, 750ms이며 이전 요청이 끝날 때까지 기다리지 않는다. 조합별 id는 구간 안에서 고유하다. HTTP 요청 제한은 25초다. ISR과 리더 회복은 각각 최대 90초 기다리고 복구 전후 대기 시간은 쓰기 지연으로 세지 않는다. 이 시간은 환경 오류를 가르는 제한이며 지연 성능 합격 기준은 아니다.

[9] `acks=all`은 현재 ISR 전체의 확인을 기다리며 최소 ISR은 성공을 허용하는 하한이다. ISR이 이미 하한 아래인 상태에서 all 쓰기를 시작하면 복제본 부족 오류를 관찰해야 한다. `acks=1`은 리더의 기록 확인만 기다리므로 같은 하한이 생산자 성공 응답을 보호하지 않는다. 소비자에게 보이는 범위는 복제와 high watermark 조건도 따른다. 복제 수는 존재할 수 있는 복제본 수이며 현재 ISR 크기와 다르다. ISR 밖 복제본을 리더로 승격하면 성공 응답을 받은 레코드도 잃을 수 있다. 복제 수 1은 원본 브로커가 돌아오기 전 대체 리더를 만들 수 없다.

[10] 합격 불변식은 조합별로 판정한다. 최초 4건은 네 조합 모두 ACK여야 한다. ISR [1]에서 quorum의 4건은 모두 NOT_ENOUGH_REPLICAS이고 나머지 세 조합의 4건은 모두 ACK여야 한다. 2번과 3번만 회복했을 때 세 복제 토픽에는 b0~b3만 남아야 한다. single의 리더는 이때 -1이어야 하고 1번 복구 뒤 b0~b3와 d0~d3가 모두 보여야 한다. receipts는 각 HTTP 결과와 일치하는 32행이어야 한다. 오류 응답, 누락, 중복, 예상 밖 레코드가 하나라도 있으면 실패다.

[11] 지표는 최대 10개다. `baseline_acks`는 최초 ACK 수 16, `degraded_acks`는 ISR 축소 뒤 ACK 수 12, `degraded_rejections`는 그 구간의 HTTP 409 수 4, `replica_errors`는 정확한 복제본 부족 본문 수 4여야 한다. `lost_acks`는 ACK된 id 중 해당 조합의 지정 읽기 시점에 없는 수 8, `retained_acks`는 그때 남은 ACK id 수 20이어야 한다. `unavailable_partitions`는 2·3번 회복 직후 리더 없는 single 파티션 수 1이다. `http_errors`는 전송 오류이거나 HTTP 200의 `ACK <id>` 또는 HTTP 409의 `NOT_ENOUGH_REPLICAS <id>` 형식에 맞지 않는 응답 수로 0이어야 한다. `observation_mismatches`는 조합별 응답·receipts 불일치 수, `record_mismatches`는 실제 레코드의 누락·추가·중복 수로 모두 0이어야 한다. 합계가 맞아도 조합별 결과가 틀리면 실패다.

[11.1] 실행 후 작업 공간의 `build/durability-observations.txt`를 열어라. 매 실행마다 이전 파일을 덮어쓴다. `baseline`, `degraded`, `survivors`, `restored` 구간의 topology, 쓰기별 `조합/id`와 HTTP 상태·본문, 읽기 시점별 `/records/<조합>`, 최종 `/receipts`가 담긴다. `observation_mismatches`가 0보다 크면 해당 구간의 조합/id로 HTTP 응답을 찾고 최종 receipts의 같은 조합·id 행과 [10]을 대조하라. `record_mismatches`가 0보다 크면 survivors의 세 복제 토픽 또는 restored의 single 레코드를 [10]과 대조하라. 실험 오류로 중단되면 마지막으로 기록된 구간을 확인하라. ERROR 행이 있으면 함께 확인하라. 서비스는 종료되므로 종료 뒤 HTTP를 호출하는 대신 이 파일로 관찰하라.

[12] 수정 범위는 `src/main/kotlin/lab/`다. 공개 경로, 조합 이름, 요청 수, 장애 순서, 프로듀서 공통 설정, `.lab-libs`와 빌드·실행 스크립트를 유지하라. 초기 코드의 불변식 실패는 실험 대상이며 빌드 실패나 시간 초과로 대체하지 마라. 실험 전 네 조합의 실패 쓰기 수, 잃는 ACK 수, 복제본 부족 오류가 처음 발생하는 구간을 각각 예측하라. 완료하려면 수정 전후 관찰 파일에서 예측과 실측의 차이를 조합별로 설명하고, HTTP 요청에서 생산자 설정·future·receipts·로그 읽기로 이어지는 경로를 추적하라. ACK를 받은 레코드가 사라지는 두 조합과 single의 복구 결과가 다른 이유도 설명하라. 설정값만 바꾼 뒤 합계만 확인하고 끝내지 마라.

[13] 공식 근거는 Kafka [Topic Configs의 min.insync.replicas와 unclean.leader.election.enable](https://kafka.apache.org/41/configuration/topic-configs/), [Producer Configs의 acks·retries·enable.idempotence](https://kafka.apache.org/41/configuration/producer-configs/), [KafkaProducer.send의 비동기 future 계약](https://kafka.apache.org/41/javadoc/org/apache/kafka/clients/producer/KafkaProducer.html), [Consumer Configs의 소켓 연결 설정 제한](https://kafka.apache.org/41/configuration/consumer-configs/)이다. 이 실험은 프로세스 장애와 오래된 복제본 승격을 다룬다. 전원 차단 때의 fsync 보장이나 실패 응답을 받은 레코드의 일반적인 부재를 증명하지 않는다. ISR 축소를 확인하고 쓰기를 시작하므로 이 실험의 quorum 거절은 append 이전 부족 상태를 재현한다.
