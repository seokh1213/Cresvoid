# 트랜잭셔널 아웃박스

[1] 주문을 PostgreSQL에 저장하고 Kafka 이벤트로 배송 준비를 요청하는 서비스를 고쳐라. DB 저장과 이벤트 발행 사이에 실패가 나도 주문과 배송 준비가 어긋나지 않아야 한다. 먼저 시작 코드로 실험해 `missing_events`, `ghost_events`, `duplicate_effects`를 관찰해라. DB 커밋과 Kafka 발행의 순서를 뒤집으면 어떤 실패가 달라지는지 예측하고, 수정 뒤 두 시나리오를 각각 세 번 통과시켜라.

[2] Docker Desktop, JDK 21과 기존 Kotlin 실행 이미지를 사용한다. Kafka는 KRaft 단일 노드이며 `order-events` 토픽의 파티션은 1개다. PostgreSQL과 Kafka는 lab-env에 고정된 이미지를 쓴다. `.lab-libs`는 `kafka` 하나다. JDBC 묶음을 합치거나 새 라이브러리를 설치하지 마라. 고정 도우미 `Postgres.kt`는 JDK 소켓으로 PostgreSQL 텍스트 프로토콜을 다룬다. trust 인증은 호스트에 DB 포트를 공개하지 않는 이 격리된 실험에서만 사용한다. 각 실행은 새 DB와 Kafka를 만들며 종료 시 컨테이너·볼륨·네트워크를 지운다.

[3] 작업 공간에서 `./build.sh`로 `build/app.jar`를 만들어라. `run.sh`는 `PORT` 환경 변수의 포트를 호스트 루프백에 공개한다. 공식 실험은 같은 진입점 `lab.MainKt`를 compose의 app 서비스로 실행하며 `PORT=8080`, `KAFKA_BOOTSTRAP=kafka:9092`, `PG_HOST=postgres`를 준다. 컨테이너 내부 HTTP 서버는 포트 전달을 받기 위해 모든 인터페이스에 바인딩한다. 서비스를 미리 띄우지 마라. 아래 명령으로 시작 코드 관찰, 예측 문항, 수정 뒤 재실험을 진행해라. 첫 실패에서 멈추므로 시작 코드에서 두 시나리오가 모두 출력되지는 않는다. 다른 작업 공간을 쓴다면 `test`에 `-dir <작업 공간 경로>`를 붙여라.

```sh
engine/bin/cresvoid start backend-lab -stage 12
engine/bin/cresvoid read backend-lab -stage 12
engine/bin/cresvoid quiz backend-lab -stage 12
engine/bin/cresvoid test backend-lab -stage 12
```

[3.1] `run.sh` 단독 실행은 공식 실험의 서비스 환경을 만들지 않는다. 실행 컨테이너에 `PORT`와 `JAVA_OPTS`만 전달하며 PostgreSQL·Kafka나 compose 네트워크를 만들지 않는다. 호스트에 `PG_HOST`와 `KAFKA_BOOTSTRAP`을 설정해도 이 스크립트는 전달하지 않는다. DB 기본 호스트명 `postgres`와 Kafka 기본 주소 `kafka:9092`는 단독 컨테이너에서 찾을 수 없으므로, 진단에는 [18]의 공개 실행 도구를 써라.

[4] `GET /health`는 DB에 연결할 수 있으면 200과 `ready`다. `POST /orders`는 줄바꿈 없는 `order-id|amount|fault` 본문을 받는다. order-id는 소문자로 시작하며 소문자·숫자·하이픈으로 된 1~30자다. amount는 1~100000인 정수이며 fault는 `normal`, `rollback`, `commit-gap` 중 하나다. 요청 본문은 UTF-8 1024바이트 이하다. 주문 ID는 실험 전체에서 고유하며 HTTP 요청 자체를 재시도하지 않는다. 같은 주문의 이벤트 ID는 항상 `evt-<order-id>`이고, Kafka 키는 order-id, 값은 `event-id|order-id|amount`다. 재발행에도 이벤트 ID와 본문을 유지해라.

[5] `OrderWorkflow.create`는 주문 저장 트랜잭션에서 `FailurePoint.beforeCommit(fault)`를 커밋 직전에 정확히 한 번 호출하고, 커밋한 뒤 `FailurePoint.afterCommit(fault)`를 정확히 한 번 호출한다. normal은 두 지점을 통과하고 202와 `accepted:<order-id>`를 반환한다. rollback은 첫 지점에서 예외가 나므로 트랜잭션을 롤백하고 409와 `rolled-back`을 반환한다. commit-gap은 커밋 뒤 두 번째 지점에서 요청 처리가 끊기며 503과 `uncertain`을 반환한다. 이 경우 주문은 이미 DB에 남아야 한다. 두 예외를 성공으로 바꾸거나 커밋 뒤 실패를 이유로 주문을 지우지 마라. commit-gap은 요청 경로 중단을 모사하며 부모 HTTP JVM을 죽이지 않는다. 실제 프로세스 종료는 [8]에서 따로 주입한다.

[6] DB 세션 하나는 `Postgres` 객체 하나다. `query(sql)`은 문자열 행 목록을 반환하며 `BEGIN`, `COMMIT`, `ROLLBACK`을 지원한다. ID 문자열은 `sqlId`로 검증해라. 고정 스키마는 주문 원장 `orders(order_id PRIMARY KEY,amount)`, 발행 작업 `outbox(event_id PRIMARY KEY,order_id,amount,sent DEFAULT false)`, 소비 처리 기록 `seen(event_id PRIMARY KEY)`, 실제 배송 준비 감사 기록 `effects(seq,event_id,order_id,amount)`다. `seq`는 자동 증가한다. `receipts(offset_no PRIMARY KEY,event_id,order_id,amount)`와 `published(seq,event_id,order_id,amount)`는 고정 코드의 관측 기록이다. 주문·이벤트·소비 기록의 관계를 유지하되 스키마를 바꾸지 마라.

[7] 부모 HTTP 서버와 Kafka 소비자는 실험 내내 살아 있다. `POST /relay/start`는 빈 본문이면 정상 발행 작업을, 유효한 이벤트 ID 본문이면 그 이벤트에서 장애를 넣는 발행 작업을 별도 자식 JVM으로 시작한다. 정상 응답은 200과 `started`, 이미 실행 중이면 200과 `running`이다. `OrderWorkflow.relay(publisher)`는 호출 시점에 미완료인 작업을 처리하고 끝낸다. 주문 부하는 이 호출 전에 끝난다. 실험기는 자식 작업이 종료된 뒤에만 다시 시작한다. 여러 발행 작업의 동시 실행이나 실행 중 새 주문의 폴링은 이 단계의 검증 범위 밖이다.

[8] `Publisher.send(order)`는 실제 Kafka 발행 확인을 기다리고 별도 DB 세션에 `published` 관측 기록을 남긴다. 지정한 장애 이벤트이면 바로 자식 JVM을 종료 코드 73으로 끝내며 함수가 돌아오지 않는다. Kafka 발행은 이미 성공했지만 호출 뒤의 처리는 실행되지 않은 상태다. 이 자식을 재시작해도 DB, Kafka, 부모 소비자는 초기화하지 않는다. 발행 확인 전부터 완료로 표시하지 마라. 재시작으로 같은 이벤트가 다시 발행되어도 소비 쪽 배송 준비 반영은 한 번이어야 한다. 생산자의 멱등 옵션만으로 서로 다른 JVM에서 발행한 중복 이벤트를 없앨 수 있다고 가정하지 마라.

[9] 커밋된 주문마다 Kafka 이벤트가 적어도 한 건 있어야 한다. 롤백된 주문의 Kafka 이벤트는 0건이어야 한다. 커밋된 주문의 이벤트 ID마다 `effects`에는 원본 주문 ID와 금액이 같은 행이 정확히 하나 있어야 한다. 같은 이벤트의 재수신은 배송 준비를 다시 만들면 안 된다. `OrderWorkflow.apply(order)`가 반환할 때 DB 반영 트랜잭션은 끝나 있어야 한다. 소비 오프셋마다 고정 코드가 먼저 `receipts`를 남기고 이 함수를 부른다. 이 단계는 Kafka에서 다시 발행한 중복을 주입하며 소비 트랜잭션 도중의 별도 프로세스 종료나 Kafka 오프셋 커밋 복구는 다루지 않는다.

[10] `GET /relay/status`는 200과 `idle`, `running`, `done:<종료 코드>` 중 하나다. 부모가 살아 있다고 발행 자식도 살아 있다고 해석하지 마라. `GET /progress`는 200과 `처리한 다음 오프셋|Kafka 끝 오프셋`이며 두 값이 같으면 현재 발행분의 소비가 끝난 상태다. `GET /state`는 200이며 주문 ID 순서의 `order-id|amount` 행을 줄바꿈으로 잇고, 주문이 없으면 빈 본문이다. 없는 경로는 404와 `missing`, 알려진 경로의 잘못된 메서드는 405와 `method`, 잘못된 본문은 400과 `invalid`, [5]의 주입 예외 이외 처리 오류는 503과 `unavailable`이다. 잘못된 주문 요청은 DB와 Kafka를 바꾸면 안 된다.

[11] `weekday-orders`는 N=8, `weekend-orders`는 N=12다. 각각 anchor 주문 한 건과 N개 묶음을 보낸다. 묶음 하나에는 normal, commit-gap, rollback 주문이 각각 한 건씩 있다. anchor의 금액은 17이며, 0부터 시작하는 묶음 i의 금액은 각각 100+i, 200+i, 300+i다. 총 요청 수는 25·37건, 커밋 주문 수는 17·25건, 롤백 주문 수는 8·12건이다. 공용 `load.main.kts`로 초당 30건, 요청 i의 예정 시각 floor(i×1000/30)ms인 개방형 부하를 보낸다. 앞 요청의 완료 여부가 다음 도착을 늦추지 않는다. HTTP 요청 타임아웃은 30초다. 합격 여부는 지연 시간 대신 개수와 관계로 판단한다.

[12] 먼저 잘못된 입력·메서드와 빈 초기 상태를 확인하고 [11]의 주문을 모두 보낸다. DB 원장과 `/state`가 기대한 커밋 주문 집합인지 검사한다. 이어 anchor 이벤트를 발행한 직후 자식이 73으로 종료되게 발행 작업을 시작하고, 소비 완료를 기다린 뒤 빈 본문으로 발행 작업을 재시작한다. 정상 작업의 종료 코드는 0이어야 하며 미완료 발행 작업은 0개여야 한다. 마지막으로 Kafka 끝 오프셋까지 소비했는지, 이벤트와 감사 행의 ID·금액이 맞는지 검사한다. 발행 자식 종료 대기와 각 소비 완료 대기는 각각 최대 45초다. 이는 시간 성능 기준이 아니라 실험 진행 상한이다. 예상과 다른 자식 종료도 최종 지표를 출력하고 불변식 실패로 처리한다.

[13] 지표는 정확히 10개다. `attempted`는 예정한 유효 주문 요청 수, `committed_orders`는 스펙에서 계산한 커밋 주문 수다. `published_records`는 Kafka 끝 오프셋까지 수신한 실제 레코드 수다. `missing_events`는 커밋 주문 중 수신 레코드가 없는 주문 수, `ghost_events`는 커밋 주문 집합에 없는 이벤트의 수신 레코드 수다. `duplicate_effects`는 이벤트 ID별 감사 행 수에서 1을 뺀 양수 부분의 합, `missing_effects`는 커밋 주문 중 감사 행이 없는 주문 수다. 이 네 오류 지표는 모두 0이어야 한다. 발행 레코드 수와 주문 수가 달라도 중복 발행만으로는 실패하지 않는다.

[14] `replayed_records`는 anchor 이벤트 수신 수에서 1을 뺀 양수 부분이며 1 이상이어야 한다. 장애 뒤 재발행을 실제로 관측했는지 확인하는 값이다. `pending_events`는 마지막 `sent=false` 행 수이며 0이어야 한다. `contract_errors`도 0이어야 한다. 이는 HTTP 응답 불일치, 원장·`/state` 불일치, 자식 종료 코드 불일치, 소비 완료 대기 실패, Kafka 끝 오프셋과 수신 수 불일치, `published`와 `receipts`의 이벤트 다중집합 불일치, 수신·감사 행의 원본 필드 오류, 전체 감사 행 수 오류, 수신되지 않은 이벤트의 감사 반영을 센다. 항목별 검사에서 실패할 때마다 1을 더하며 같은 원인이 여러 항목에 걸리면 여러 번 셀 수 있다. 합격하려면 전체 감사 행 수도 커밋 주문 수와 같아야 한다. 빌드 실패·시간 초과·지표 누락은 의도된 불변식 실패가 아니다.

[15] 수정 범위는 `src/main/kotlin/Workflow.kt`의 `OrderWorkflow`와 같은 파일에 추가하는 선언이다. `create`·`relay`·`apply`의 공개 시그니처, `FailurePoint` 호출 위치 계약과 `Publisher.send` 경계는 유지해라. `Main.kt`, `Order.kt`, `Kafka.kt`, `Postgres.kt`, DB 스키마, 빌드·실행 스크립트와 환경 변수는 바꾸지 마라. `receipts`, `published`는 읽거나 쓰지 마라. 소비·발행 관측과 오프셋을 꾸미거나 감사 행을 나중에 지우지 마라. 이벤트 ID·금액·시나리오명으로 실험을 알아내는 분기, 입력 집합을 미리 아는 구현, 새 의존성과 외부 부하 도구는 금지한다. `practice/`, 다른 단계와 숨은 파일도 바꾸지 마라.

[16] 실패를 좁힐 때 `missing_events`가 양수이면 커밋 뒤 끊긴 요청의 발행 작업이 남는지, `ghost_events`가 양수이면 롤백 전에 발행한 이벤트가 있는지 확인해라. `duplicate_effects`가 양수이면 [8]의 발행 재시작을 소비가 중복 반영했는지 추적해라. `replayed_records=0`이면 자식이 실제로 73으로 끝났는지와 발행 확인 뒤 완료 기록의 경계를 확인해라. 예를 들어 `sample|9|commit-gap`은 503과 `uncertain`이어도 주문 원장에는 `sample|9`가 남고, 복구 뒤 이벤트는 `evt-sample|sample|9`, 감사 반영은 한 행이어야 한다. `sample|9|rollback`은 주문·이벤트·감사 모두 없어야 한다. 두 예시는 각각 빈 DB에서 실행하는 별개 요청이다.

[17] 설계 근거는 microservices.io의 [Transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html) 패턴이다. 같은 DB 트랜잭션에 발행할 메시지를 보관하고 별도 발행자가 전달하는 경계를 설명하며, 발행자가 중복 전달할 수 있으므로 소비의 멱등성도 필요하다고 명시한다. API 근거는 PostgreSQL 공식 [Transactions](https://www.postgresql.org/docs/current/tutorial-transactions.html)와 [메시지 흐름](https://www.postgresql.org/docs/current/protocol-flow.html), Kafka 공식 [KafkaProducer](https://kafka.apache.org/41/javadoc/org/apache/kafka/clients/producer/KafkaProducer.html), JDK 21 [HttpServer](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html)다. DB 트랜잭션이 Kafka 발행까지 원자적으로 만드는 것은 아니다. 이 실험의 장애 지점·부하·관측 대기는 교육용 계약이며 DB 자체 장애나 네트워크 분할의 모든 경우를 보장하지 않는다.

[18] `contract_errors`만 남으면 공개 도구 `diagnostics/diagnose.main.kts`와 `diagnostics/compose.yaml`로 작은 입력을 재현해라. 작업 공간을 빌드한 뒤 프로젝트 루트에서 아래 명령을 실행해라. 숨은 검사나 정답은 열지 않는다. 도구는 공식 실험과 같은 이미지·서비스 환경에서 별도 DB와 Kafka를 띄우고 HTTP 응답, 발행 자식 상태, 소비 오프셋, 각 단계의 DB 행을 출력한다. 끝나거나 중단되면 이 실행의 컨테이너·볼륨·네트워크를 지운다. 공식 test의 DB는 종료와 함께 삭제되므로 이 도구는 새 입력으로 별도 실행한다. 작은 입력 재현의 성공이 전체 실험 통과를 뜻하지는 않는다.

```sh
kotlin content/tracks/backend-lab/stages/12-outbox/diagnostics/diagnose.main.kts /absolute/path/to/workspace
```

[19] 진단 입력은 `sample|9|commit-gap`, `cancelled|8|rollback`, `anchor|17|normal`이다. HTTP 응답은 차례로 503과 `uncertain`, 409와 `rolled-back`, 202와 `accepted:anchor`여야 한다. 원장과 `/state`에는 anchor=17, sample=9만 남아야 한다. 도구는 anchor 발행 직후 자식을 종료시키고 정상 자식을 다시 시작한다. `/relay/status`가 차례로 `done:73`, `done:0`인지 보고 매번 `/progress`의 두 오프셋이 같아졌는지 확인해라. `outbox`의 ID·금액·sent를 발행 전, 장애 뒤, 복구 뒤로 비교해라. 복구 뒤 미완료 행은 없어야 한다.

[19.1] `published`와 `receipts`는 순서 대신 `(event_id,order_id,amount)`별 개수가 같은지 비교하고 수신 수가 끝 오프셋과 같은지 확인해라. 수신·감사 행은 원장과 이벤트 ID·주문 ID·금액이 맞아야 하며, `effects`에는 각 커밋 주문이 한 행씩만 있어야 한다. 수신 없이 감사만 생긴 행이나 롤백 주문의 이벤트도 없어야 한다. `seen`과 `effects`를 함께 보면 중복 방지 기록과 배송 준비 반영의 경계를 좁힐 수 있다. 이 도구의 관측 조회와 달리 제출 코드에서는 [15]의 `receipts`·`published` 접근 금지를 지켜라.
