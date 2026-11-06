# 멱등 소비자와 순서

[1] Kafka 이벤트를 PostgreSQL 상태에 반영하는 소비자를 고쳐라. 먼저 시작 코드를 실험해 `duplicate_effects`, `version_regressions`, `state_mismatches`를 관찰해라. 같은 이벤트가 여러 번 도착하는 경우와 서로 다른 버전이 뒤늦게 도착하는 경우를 구분해라. 수정 뒤 두 시나리오를 각각 세 번 실행해라.

[2] Docker Desktop, JDK 21과 기존 Kotlin 실행 이미지를 사용한다. Kafka는 고정 이미지의 KRaft 단일 노드이며 토픽 `events`는 파티션 3개다. PostgreSQL도 고정 이미지를 사용한다. 각 실험은 독립 compose 프로젝트를 만들고 끝나면 컨테이너·볼륨·네트워크를 지운다. `.lab-libs`는 `kafka` 하나다. JDBC 묶음을 합치거나 새 라이브러리를 설치하지 마라. 제공된 `Postgres.kt`는 JDK 소켓으로 PostgreSQL의 텍스트 프로토콜을 다루는 고정 도우미다. trust 인증은 호스트에 포트를 열지 않는 이 실험 DB에만 사용한다.

[3] `./build.sh`는 `build/app.jar`를 만든다. `run.sh`는 `PORT` 환경 변수의 포트를 호스트 루프백에 공개한다. 실험에서는 같은 진입점 `lab.MainKt`를 compose의 app 서비스로 띄우고 내부 `PORT=8080`, `KAFKA_BOOTSTRAP=kafka:9092`, `PG_HOST=postgres`를 준다. 서비스를 직접 미리 띄우지 마라. HTTP 서버는 부모 JVM에 있고 실제 Kafka 소비자는 별도 자식 JVM이다. 부모 서버의 생존을 소비자의 생존으로 해석하지 마라.

[3.1] 저장소 루트에서 아래 명령을 순서대로 실행해라. `start`가 작업 공간을 만들면 그 안의 시작 코드를 읽고 `quiz`의 예측 문항을 모두 마쳐라. 이어서 `test`로 공식 실험을 실행해라. 자체 compose를 만들거나 `hidden/`을 열 필요는 없다. 엔진이 빌드, 서비스 시작, 부하, 측정과 종료를 맡는다.

```sh
engine/bin/cresvoid start backend-lab -stage 11
engine/bin/cresvoid read backend-lab -stage 11
engine/bin/cresvoid quiz backend-lab -stage 11
engine/bin/cresvoid test backend-lab -stage 11
```

[3.2] 시작 코드의 실패 지표를 적고 작업 공간의 코드를 고친 뒤 같은 `test` 명령을 다시 실행해라. 성공하면 `morning-traffic (1/3)`부터 `evening-traffic (3/3)`까지 총 6회가 통과한다. 첫 실패에서 멈추므로 시작 코드에서 두 시나리오가 모두 출력되지는 않는다. 재실행은 매번 새 DB와 Kafka를 쓰며, 한 시나리오 안의 소비자 재시작만 DB와 Kafka를 유지한다. 다른 경로에 구현했다면 `test`에 `-dir <작업 공간 경로>`를 붙여라.

[4] `GET /health`는 DB에 접속할 수 있으면 200과 `ready`를 반환한다. `POST /events`는 줄바꿈 없는 `event-id|entity-id|version|value|delta` 본문을 받는다. ID 둘은 소문자로 시작하고 소문자·숫자·하이픈으로 된 1~40자다. version은 1~1000, value는 -100000~100000, delta는 -1000~1000인 정수다. 본문은 UTF-8 1024바이트 이하다. Kafka가 발행을 확인하면 202와 `queued:<event-id>`를 반환한다. 이 응답은 DB 반영 완료를 뜻하지 않는다. 엔터티 ID를 Kafka 키로 사용한다.

[5] 같은 이벤트 ID의 본문은 항상 같다. 서로 다른 이벤트는 같은 엔터티를 가리킬 수 있지만 같은 엔터티의 같은 버전은 하나의 이벤트만 가진다. 버전의 도착 순서는 보장하지 않는다. value는 해당 버전의 전체 스냅숏이고 delta는 그 이벤트만의 증감량이다. 오래된 이벤트라도 처음 받은 이벤트라면 delta를 정확히 한 번 합산해야 한다. 최신 value에 delta를 더하는 계약이 아니다.

[6] `entities(entity_id,version,value,total)`의 초기 상태는 버전·값·합계 모두 0이다. 모든 고유 이벤트의 delta 합이 total이 되어야 한다. version과 value는 해당 엔터티에서 받은 가장 큰 버전과 그 버전의 value여야 한다. 예를 들어 `e-a|item-a|2|90|7`, `e-b|item-a|1|40|-2`, `e-a|item-a|2|90|7`을 처리하면 최종 상태는 `item-a|2|90|5`다. 엔터티 하나의 높은 버전 때문에 다른 엔터티의 낮은 버전을 버리지 마라.

[7] `effects`는 실제 비즈니스 반영의 감사 기록이다. 각 고유 이벤트마다 정확히 한 행을 남겨라. `event_id,entity_id,version,value,delta`에는 입력값을, `previous_version`에는 반영 직전의 엔터티 버전을, `after_version`에는 반영 직후의 엔터티 버전을 적는다. 이미 반영한 이벤트의 재수신은 total·스냅숏·effects 어느 것도 바꾸면 안 된다. 처음 받은 옛 버전의 이벤트는 감사 행과 total을 바꾸되 스냅숏 버전을 낮추면 안 된다. `after_version < previous_version`인 감사 행은 0건이어야 한다.

[8] `Postgres.query(sql)`은 결과를 문자열 행 목록으로 반환하며 같은 객체의 호출은 같은 DB 세션이다. `BEGIN`, `COMMIT`, `ROLLBACK`을 사용할 수 있다. 추가 테이블은 `seen(event_id PRIMARY KEY)`다. `sqlId`는 허용된 ID만 SQL 리터럴로 바꾼다. 함수가 성공 반환할 때 DB 트랜잭션은 끝나 있어야 한다. 메서드 밖의 DB 쓰기와 오프셋 처리는 고정 코드의 책임이다.

[9] `POST /control/start`는 빈 본문이면 정상 소비자를, 유효한 이벤트 ID 본문이면 그 ID에서 장애를 넣는 소비자를 시작한다. 응답은 200과 `started`이며 이미 실행 중이면 `running`이다. 장애는 `EventHandler.apply` 성공 반환 뒤, 해당 레코드의 Kafka 오프셋 커밋 직전에 자식 JVM을 종료 코드 73으로 즉시 종료한다. 재시작은 같은 그룹 `projection`을 사용한다. 자동 커밋은 꺼져 있고 레코드별 DB 반영이 끝난 뒤 다음 오프셋을 명시적으로 커밋한다. 시작할 때 소비 위치를 끝으로 옮기지 마라.

[10] `GET /status`는 200과 `idle`, `running`, `dead:<종료 코드>` 중 하나다. `GET /progress`는 200과 `커밋 오프셋 합|토픽 끝 오프셋 합`이다. `GET /state`는 200이며 엔터티 ID 순서의 `entity_id|version|value|total` 행들을 줄바꿈으로 잇는다. 데이터가 없으면 빈 본문이다. 알려진 경로의 잘못된 메서드는 405와 `method`, 없는 경로는 404와 `missing`, 잘못된 본문은 400과 `invalid`, 처리 예외는 503과 `unavailable`이다. 잘못된 이벤트는 Kafka에 넣지 않는다. 예를 들어 `GET /events`는 405다.

[11] `morning-traffic`은 일반 엔터티 6개, `evening-traffic`은 9개를 사용한다. 두 시나리오 모두 먼저 별도 엔터티의 이벤트 한 건을 발행한 뒤 그 이벤트에서 죽도록 소비자를 시작한다. 40초 안에 `dead:73`이 되는지 확인하고, DB 반영이 남았지만 Kafka 커밋은 0인 상태를 확인한다. 빈 본문으로 소비자를 재시작하고 같은 Kafka 레코드가 다시 전달되는지 확인한다. 서버나 DB를 초기화하지 않는다. `deliveries`는 고정 코드가 실제 수신마다 남기는 기록이며 비즈니스 감사 기록과 별개다.

[12] 이후 각 일반 엔터티의 버전 6 이벤트를 두 번씩, 초당 60건의 개방형 일정으로 보낸다. 그 레코드들이 커밋된 뒤 버전 2, 5, 1, 4, 3 이벤트를 각각 세 번씩 보내고 엔터티들을 섞는다. 마지막 부하에는 처음 장애를 넣은 이벤트도 세 번 더 넣는다. 두 번째 부하도 초당 60건이다. 요청은 앞 응답을 기다리지 않고 예정 시각에 보낸다. 엔터티별 최신 버전이 먼저 반영된 뒤 옛 버전이 오는 조건을 고정하고, 서로 다른 파티션 사이의 처리 순서는 가정하지 않는다. HTTP 요청 타임아웃은 20초, 각 소비 완료 대기는 40초다. 시간은 처리량 합격 기준이 아니라 실험의 진행 상한이다.

[13] 최초 발행까지 포함한 Kafka 발행 수는 각각 106건과 157건, 고유 이벤트 수는 각각 37건과 55건이다. DB 반영 전 장애나 DB 커밋 자체의 실패, 리밸런스와 다중 소비자의 동시 처리는 이 단계의 주입 범위 밖이다. 이 실험의 장애는 DB 커밋 뒤 Kafka 커밋 전 재처리를 대상으로 한다. 처리 기록과 상태를 따로 커밋하는 구현의 안전성을 이 결과만으로 일반화하지 마라.

[14] 실험기는 종료 전에 Kafka 커밋 위치, DB 감사 행 전체, 엔터티 상태와 공개 HTTP 응답을 교차 검사한다. `published`는 예정한 유효 발행 요청 수, `unique_events`는 입력의 고유 ID 수, `effect_rows`는 DB 감사 행 수다. `duplicate_effects`는 ID별 감사 행 수에서 1을 뺀 양수 부분의 합, `missing_effects`는 감사 행이 없는 고유 ID 수다. 둘 다 0이어야 하며 `effect_rows = unique_events`여야 한다. `version_regressions`는 [7]의 버전 역행 행 수이며 0이어야 한다.

[15] `state_mismatches`는 입력으로 계산한 기준 상태와 다른 엔터티 수다. 없는 엔터티와 예상 밖 엔터티도 각각 한 건으로 센다. 최종 상태뿐 아니라 최신 버전 부하 직후 상태도 검사하고 두 결과를 더한다. 이 지표는 0이어야 한다. `replayed_records`는 장애 이벤트의 마지막 재발행 부하를 보내기 전까지 `deliveries`에서 센 최초 수신 이후 추가 수신 수다. 1 이상이어야 한다. 이는 HTTP로 같은 내용을 다시 발행한 횟수가 아니라 Kafka의 미커밋 레코드 재수신이다.

[16] `contract_errors`는 HTTP 예시·제어 응답·발행 응답을 요청별로 검사해 상태 코드나 본문이 하나라도 다르면 1건으로 센다. 소비 완료 대기 실패와 장애 종료 상태 대기 실패도 대기마다 1건으로 센다. 장애 시점에 해당 이벤트의 DB 감사 행이 없거나 Kafka 커밋 위치가 잘못되면 각각 1을 더한다. 감사 행마다 원본 다섯 필드 묶음, 직전 버전, 직후 버전을 따로 검사해 틀린 항목마다 1을 더한다. 원본 필드 여러 개가 틀려도 필드 묶음의 오류는 1건이다. 직전 버전은 앞선 감사 행들로 재계산한 해당 엔터티의 버전이고, 직후 버전은 그 버전과 이벤트 버전 중 큰 값이어야 한다. 빈 입력에는 400, 잘못된 메서드에는 405, 없는 경로에는 404를 반환해야 하며 발행 응답에는 해당 ID가 있어야 한다. DB 감사 순서대로 엔터티별 version·value·total을 재계산해 실제 상태와 비교하고 다른 엔터티마다 1을 더한다. 최종 HTTP `/state`도 같은 DB 상태여야 한다. 503과 통신 실패도 요청별 오류다. 총 지표는 10개이며 `contract_errors`는 0이어야 한다. 준비 실패·빌드 실패·필수 지표 누락·실험 전체 시간 초과는 의도된 불변식 실패로 세지 않는다.

[16.1] `contract_error_mask`는 오류 종류를 구별하는 보조 지표다. HTTP 응답은 1, 소비 완료 대기는 2, `dead:73` 대기는 4, 장애 직후 감사 행 누락이나 Kafka 커밋 위치 오류는 8, 감사 원본 필드 오류는 16, 직전 버전 오류는 32, 직후 버전 오류는 64, 감사 재계산 상태와 DB 상태 불일치는 128이다. 발생한 종류의 값만 한 번씩 더한다. 같은 종류가 여러 번 발생해도 중복해서 더하지 않는다. 예를 들어 65면 HTTP 응답과 직후 버전 오류가 함께 있다. 정상값은 0이다. 실패 화면의 이 값과 아래 절차로 원인을 좁혀라. 원시 오류 출력은 엔진이 보여 주지 않는다.

[16.2] 같은 `test` 명령으로 실패를 재현해라. 값 1은 [4]·[9]·[10]의 상태 코드와 본문을 점검해라. 값 2는 `/progress`의 두 오프셋 합이 예정 발행 수에 도달하는지, 값 4는 부모 `/health`가 정상이어도 자식 `/status`가 `dead:73`이 되는지 확인해라. 값 8은 첫 장애 직후 `effects`에 해당 ID가 있고 `/progress`는 `0|1`인지 확인해라. 소비자가 DB 오류로 먼저 죽으면 값 2·4도 함께 생길 수 있다. 이때 버전 판정보다 `apply`의 SQL 실패와 트랜잭션 종료 여부부터 추적해라.

[16.3] DB 감사 오류는 작은 입력으로 추적해라. 초기 상태에 [6]의 세 이벤트를 순서대로 적용했을 때 `effects`의 원본 필드는 처음 두 입력과 같고, 감사 버전은 각각 `0|2`, `2|2`여야 한다. 세 번째 입력은 감사 행을 늘리지 않는다. 값 16은 입력 다섯 필드와 감사 필드를, 32는 앞선 감사 행에서 구한 엔터티 버전과 `previous_version`을, 64는 그 버전과 입력 버전 중 큰 값과 `after_version`을 비교해라. 값 128은 감사 행을 `seq` 순으로 읽어 엔터티별 상태를 재계산하고 `entities`와 비교해라. `/state`는 같은 DB 조회 결과와 같아야 한다.

[16.4] 실행 중 DB를 직접 확인하려면 두 번째 터미널에서 `docker ps --filter label=com.docker.compose.service=app --format '{{.ID}} {{.Label "com.docker.compose.project"}}'`로 앱을 찾아라. `docker inspect <앱 ID> --format '{{range .Mounts}}{{.Source}} {{end}}'`가 자신의 작업 공간을 가리키는지 확인해라. 아래 `<프로젝트 이름>`은 그 앱의 compose 프로젝트 이름이다. 실험은 종료 시 자원을 지우므로 관측 명령을 준비한 뒤 `test`를 다시 실행해라. 조회만 하고 이벤트 발행이나 제어 요청은 추가하지 마라. 다른 실험의 컨테이너를 선택하지 마라.

```sh
docker exec <앱 ID> curl -sS -i localhost:8080/status
docker exec <앱 ID> curl -sS -i localhost:8080/progress
docker exec <앱 ID> curl -sS -i localhost:8080/state
docker ps --filter label=com.docker.compose.project=<프로젝트 이름> --filter label=com.docker.compose.service=postgres --format '{{.ID}}'
docker exec <DB ID> psql -X -U lab -d lab -c 'SELECT * FROM effects ORDER BY seq; SELECT * FROM entities ORDER BY entity_id; SELECT * FROM deliveries ORDER BY seq;'
```

[17] 수정 범위는 `src/main/kotlin/Consumer.kt`의 `EventHandler`와 여기서 호출하는 새 Kotlin 선언이다. `consume` 함수, `Main.kt`, `Event.kt`, `Postgres.kt`, DB 스키마, 빌드·실행 파일과 환경 변수 계약은 유지해라. 감사 기록·수신 기록·오프셋을 꾸미거나 지우지 마라. 이벤트 내용으로 시나리오를 알아내는 분기나 입력 전체를 미리 아는 구현도 금지한다. `practice/`, 숨은 파일과 다른 단계는 수정하지 마라.

[18] 참고 자료는 Kafka 공식 [Message Delivery Semantics](https://kafka.apache.org/41/design/design/#message-delivery-semantics)와 [Consumer Configs의 enable.auto.commit](https://kafka.apache.org/41/configuration/consumer-configs/#enable.auto.commit), PostgreSQL 공식 [메시지 흐름](https://www.postgresql.org/docs/current/protocol-flow.html)이다. 장애 뒤 관측 결과와 [5]~[7]의 불변식을 바탕으로 처리 경계를 정해라.
