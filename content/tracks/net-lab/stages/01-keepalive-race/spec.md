# keep-alive 수명의 경합

[1] 먼저 관찰하고 나서 구현해라. 상품 조회와 주문 접수를 하위 HTTP 서비스에 전달하는 중계 서비스를 다룬다. 시작 코드로 실험하면 짧은 간격의 조회는 성공하지만 서버의 유휴 수명 근처에서 일부 조회가 실패한다. 요청을 서버가 처리했어도 응답을 받지 못할 수 있으므로 조회 복구와 주문 중복 방지를 함께 만족시켜라.

[2] HTTP/1.1 연결을 재사용하면 연결을 맺는 비용을 줄일 수 있다. 이 단계에서는 짧은 간격의 요청과 긴 공백 뒤 요청을 같은 lane에 보내고, 응답이 없는 장애도 따로 만든다. 시작 코드가 각 상황에서 어떤 연결을 쓰고 다음 요청에 어떤 상태를 남기는지 추적해라.

[3] JDK 21과 Kotlin만 사용해라. `build.sh`로 실행 jar를 만들고 `run.sh`로 서버를 시작해라. 서버는 환경 변수 `PORT`가 지정한 `127.0.0.1` 포트에서 수신하며 하위 서비스는 `UPSTREAM_PORT`가 지정한 루프백 포트다. `GET /health`는 하위 연결을 만들지 않고 HTTP 200, 본문 `ready`를 반환한다. 외부 서비스와 새 의존성은 필요 없다.

[4] 공개 요청은 본문이 없는 `GET` 또는 `POST /relay?lane=<이름>&id=<식별자>&fault=<종류>`다. `lane`과 `id`는 영문 소문자·숫자·하이픈으로 된 1~40자이며 `fault`는 `none`, `once`, `always` 중 하나다. 생략한 `fault`는 `none`이다. 그 밖의 값과 메서드에는 HTTP 400, 본문 `invalid-request`를 반환한다. 실험은 유효한 값만 보낸다. `lane`별로 연결 상태를 나누고 같은 lane의 하위 교환은 직렬화해라. lane과 id는 요청을 묶거나 관찰하기 위한 값이며 주문을 중복 제거하는 키가 아니다.

[5] 하위 서비스에는 메서드를 유지한 채 `/resource?id=<식별자>&fault=<종류>`로 HTTP/1.1 요청을 보내라. `Host: 127.0.0.1`, `Content-Length: 0`을 쓰고 파이프라이닝은 하지 마라. 하위 서버는 Content-Length가 있는 HTTP 200 응답을 보낸다. GET의 본문은 `GET:<id>`이며 POST는 수신할 때마다 해당 id의 주문 수를 하나 늘리고 `POST:<id>:<누적 수>`를 보낸다. 정상 응답의 상태와 본문을 그대로 전달해라. 연결·읽기·쓰기 실패나 불완전한 응답은 실패한 연결을 폐기한 뒤 최종적으로 HTTP 502, 본문 `upstream-unavailable`로 전달한다. 완전한 HTTP 응답을 I/O 실패로 취급하여 다시 보내지 마라.

[6] 하위 서버는 응답 이후 다음 요청이 없는 연결을 5,000ms 뒤 닫는다. 실험기가 이 서버와 중계 서비스를 시작하고 종료한다. `fault=once`는 같은 메서드와 id로 처음 받은 요청을 처리한 뒤 응답 없이 연결을 닫고, 두 번째부터 정상 응답한다. `fault=always`는 매번 처리한 뒤 응답 없이 닫는다. POST에서는 응답 유실 전에도 주문 수가 늘어난다. 이 장애는 유휴 연결 종료와 별개이므로 유휴 수명을 줄여도 발생한다.

[7] `session-traffic`은 공용 개방형 부하 도구로 시작 후 0, 300, 600, 5,100, 10,600, 15,500, 20,600ms에 같은 lane으로 서로 다른 id의 정상 GET을 보낸다. 앞선 요청의 완료를 기다려 다음 요청 시각을 미루지 않는다. 요청 간격은 300, 300, 4,500, 5,500, 4,900, 5,100ms다. 정상 조회 7건은 모두 HTTP 200과 정확한 본문으로 끝나야 한다. 각 id가 하위 서버에 정확히 한 번 도착해야 하며 처음 세 요청은 하위 연결 하나를 공유해야 한다. 요청마다 새 연결을 만드는 방식으로 통과하지 마라.

[8] 정상 조회 부하가 끝난 뒤 새로운 기준 시각으로 장애 부하를 보낸다. 0ms에 별도 lane으로 `once` GET, 400ms에 다른 lane으로 `always` GET, 800ms에 주문 lane으로 `once` POST, 1,200ms에 같은 주문 lane으로 새 id의 정상 POST, 1,600ms에 앞서 `always` GET을 보낸 lane으로 새 id의 정상 GET을 보낸다. `once` GET은 새 연결에서 한 번 재시도해 HTTP 200이어야 하며 하위 수신은 합계 두 번이어야 한다. `always` GET도 시도는 합계 두 번이어야 하며 HTTP 502로 끝나야 한다. 재시도까지 실패하면 더 보내지 마라.

[9] POST는 I/O 실패 뒤 자동으로 재시도하지 마라. `once` POST는 HTTP 502이며 하위 주문 수는 정확히 1이어야 한다. 그 다음 정상 POST는 새 id의 주문 수 1과 HTTP 200을 반환해야 한다. 앞서 GET이 실패한 lane의 정상 GET도 HTTP 200이며 하위 수신은 한 번이어야 한다. 장애 부하의 다섯 요청이 모두 완료된 시점을 새 기준으로 삼아 6,000ms 뒤 같은 주문 lane에 새 id의 정상 POST를 한 번 보낸다. 그 사이 이 lane에는 다른 요청을 보내지 않는다. 이 POST도 HTTP 200, 본문 `POST:<id>:1`이어야 하며 하위 주문 수는 정확히 1이어야 한다. 이 단계의 메서드 중 자동 재시도를 허용하는 것은 멱등한 GET뿐이다.

[10] 실험 지표 `normal_failures`는 정상 조회 7건 중 상태·본문·전송 오류가 있는 수다. `read_recovery_failures`, `retry_budget_failures`, `write_safety_failures`는 각각 once GET, always GET, once POST가 [8]~[9]의 상태·본문·하위 수신 횟수 중 하나라도 어기면 1, 모두 맞으면 0이다. `recovery_failures`는 장애 후 정상 POST와 GET, 6,000ms 공백 뒤 정상 POST의 세 요청 중 [9]의 상태·본문·하위 수신 횟수를 어긴 요청 수다. `reuse_failures`는 최초 세 GET이 모두 한 연결로 수신되면 0, 아니면 1이다. `normal_count_failures`는 하위 수신이 정확히 한 번이 아닌 정상 조회 id의 수다. 이 일곱 지표가 모두 0이어야 합격한다. 모든 502의 본문도 [5]와 같아야 한다.

[11] `write_applications`는 once POST가 실제로 처리된 횟수이며 합격 시 1이다. `server_idle_closes`는 하위 서버가 유휴 수명으로 닫은 연결 수다. `p95_ms`는 13개 공개 요청의 전송 시작부터 본문 수신 또는 오류까지 걸린 시간의 오름차순 95백분위수이며 올림 순위를 쓴다. 뒤의 두 지표는 관찰용으로 합격 임계값이 없다. 공개 요청마다 8초 제한을 두고 준비 확인은 30초, 시나리오 전체는 90초로 제한한다. 장시간 미완료를 정상 실패로 세지 마라. 판정은 밀리초 성능 경쟁 대신 상태·본문·실제 수신 개수와 연결 재사용 관계로 한다.

[12] `cresvoid start net-lab -stage 1`로 작업 공간을 만들고 코드를 읽은 뒤 예측 문항을 풀어라. `cresvoid test net-lab -stage 1`을 실행해 시작 코드의 지표를 관찰해라. 300ms와 5초 부근에서 연결의 상태가 어떻게 달라질지 예측한 결과를 `normal_failures`, `server_idle_closes`, `write_applications`와 비교해라. 긴 공백 뒤 POST는 `recovery_failures`와 함께 살펴라. 구현은 `src/main/kotlin/ConnectionPool.kt`만 고쳐라. [5]~[10]의 계약을 만족하도록 수정한 뒤 같은 명령으로 확인해라. `Main.kt`, `UpstreamConnection.kt`, 공개 계약, 빌드·실행 파일, 하위 서버와 실험기는 바꾸지 마라. 엔진 명령은 프로젝트 루트의 `engine/bin/cresvoid`를 사용한다.

[13] 근거로 [RFC 9112 §9.3.1](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3.1)의 비동기 연결 종료 복구, [§9.5](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.5)의 유휴 종료 경합, [RFC 9110 §9.2.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.2)의 멱등성과 자동 재시도 조건을 읽어라. [JDK 21 HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html)는 인스턴스가 연결 풀을 관리하고 재사용한다고 설명한다. 실제 HttpClient의 내부 복구 방식에 따라 유휴 종료가 호출자에게 오류로 보이지 않을 수도 있다. 이 단계는 그 내부 동작을 그대로 재현하는 대신 JDK Socket 기반의 작은 HTTP/1.1 클라이언트를 제공하여 연결 대여와 재시도를 직접 관찰한다. [Socket API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Socket.html)의 `isConnected`, `setKeepAlive`, `setSoTimeout`도 확인해라.
