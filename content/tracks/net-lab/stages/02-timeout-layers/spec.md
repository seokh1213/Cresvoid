# 타임아웃의 층

[1] 하위 서비스 호출에 연결 수립, 응답 헤더 대기, 요청 전체의 시간 제한을 구현해라. 프로젝트 루트에서 `engine/bin/cresvoid start net-lab -stage 2`로 작업 공간을 만들고 시작 코드를 읽어라. `engine/bin/cresvoid quiz net-lab -stage 2`로 확인 문항을 푼 뒤 `engine/bin/cresvoid test net-lab -stage 2`로 시작 코드의 지표를 관찰해라. 어떤 구간에 제한이 없는지 예측한 뒤 수정하고 같은 실험을 세 번 반복해 확인해라. 이 단계는 JDK 21과 Kotlin 표준 라이브러리만 사용한다. `JAVA_HOME`과 `PATH`의 `java`가 모두 JDK 21을 가리키는지 확인해라.

[2] `build.sh`는 `build/app.jar`를 만들고 `run.sh`는 환경 변수 `PORT`가 지정한 `127.0.0.1` 포트에서 HTTP 서비스를 실행한다. 공개 API는 `GET /health`, `GET /call?kind=<종류>&token=<식별자>`, `GET /stats`, `GET /reset`이다. `kind`는 `fast`, `connect`, `headers`, `body`, `drop` 중 하나이고 token은 영문자와 숫자로 된 식별자다. `fast`가 성공하면 HTTP 200과 `ok:<식별자>`를 반환해라. 시간 제한이면 HTTP 504와 `CONNECT_TIMEOUT`, `RESPONSE_TIMEOUT`, `TOTAL_TIMEOUT` 중 실제 작동한 층을 반환해라. 하위 연결이 끊어지면 HTTP 502와 `IO_ERROR`를 반환해라. 본문에는 줄바꿈을 붙이지 마라.

[3] 실험기는 환경 변수 `UPSTREAM_URL`에 HTTP TCP 프록시 주소, `CONNECT_URL`에 TLS 연결 지연 프록시 주소를 전달한다. `connect`는 `CONNECT_URL`을 호출하고 나머지는 `UPSTREAM_URL/<종류>?token=<식별자>`를 호출해라. 각 요청은 실제 하위 통신을 수행해야 한다. 정상 응답 본문을 그대로 전달해라. 애플리케이션 재시도는 추가하지 마라. JDK 자체의 연결 복구 재시도는 허용한다. 외부 인터넷이나 고정 포트를 사용하지 마라.

[4] 연결 제한은 500ms, 응답 헤더 대기 제한은 1500ms, 요청 전체 제한은 2500ms로 설정해라. 응답 헤더 대기 제한은 하위 요청을 시작할 때부터 적용하므로 연결 수립 시간도 포함한다. 전체 제한은 `Gateway.call` 진입부터 본문을 전부 읽을 때까지 적용하고 이미 쓴 시간을 빼야 한다. 연결 수립에는 이 실험의 TLS 핸드셰이크도 포함한다. 이미 연결된 프록시가 응답 헤더를 주지 않는 장애와 헤더를 준 뒤 본문을 계속 늦추는 장애를 구별해라. 시간 제한이 발생하면 진행 중인 하위 통신을 취소하고 자원을 회수해라.

[5] 실험기는 Kotlin 하위 HTTP 서버 앞에 TCP 프록시를 둔다. `fast`는 즉시 전달한다. `connect` 프록시는 TCP를 받아 TLS 핸드셰이크를 6500ms 동안 진행하지 않는다. 이는 TCP SYN 손실을 흉내 내는 실험이 아니다. `headers`는 하위 응답 전체를 6500ms 늦춘다. `body`는 헤더를 즉시 전달한 뒤 본문을 400ms 간격으로 한 바이트씩 보내며 6500ms에 연결을 닫는다. `drop`은 요청을 받은 뒤 응답 없이 연결을 닫는다. 따라서 `connect`, `headers`, `body`는 각각 연결, 응답 헤더, 전체 제한으로 끝나야 하고 `drop`은 I/O 오류로 끝나야 한다.

[6] 준비 확인 후 정상 요청 3개로 워밍업하고 `/reset`으로 플랫폼 스레드 기준값을 잡는다. `first-pass`는 10요청/초로 3초, `return-pass`는 20요청/초로 3초 동안 요청을 보낸다. 다섯 종류를 `fast`, `connect`, `headers`, `body`, `drop` 순서로 반복하며 이전 응답과 무관하게 예정된 시각에 출발한다. 각각 30개와 60개 요청이며 종류별 개수는 6개와 12개다. 부하 응답을 모두 모은 뒤 정상 요청 5개를 추가해 회복을 확인하고 800ms 뒤 통계를 읽는다. 이때 프록시에 열린 연결도 0개여야 한다. 외부 측정의 요청 제한은 10초이며 이는 합격 상한을 대신하지 않는다. 외부 측정 시간 초과와 실험기 오류는 불변식 실패와 구별한다.

[7] 모든 요청은 클라이언트 관측 4500ms 안에 끝나야 한다. 요청 수와 종류별 상태·본문이 모두 일치하고 정상 회복 요청 5개가 성공해야 한다. 시간 여유 2000ms는 스케줄링과 루프백 왕복을 위한 것이며 6500ms 지연을 기다리는 구현은 통과할 수 없다. 실험기는 프록시에서 각 종류의 실제 연결·요청 도착 수도 확인한다. 다섯 종류 모두 예정된 횟수 이상 관측되어야 한다.

[8] `/stats`와 `/reset`의 계측은 시작 코드가 제공한다. `ThreadMXBean.threadCount`로 플랫폼 스레드를 20ms마다 표본 추출하고 `/reset` 이후 기준값 대비 최대 증가량과 마지막 증가량을 구한다. 음수인 증가량은 0으로 센다. 최대 증가는 40개 이하, 마지막 증가는 16개 이하여야 한다. 가상 스레드는 이 값에 포함되지 않으므로 별도로 실행 중인 `Gateway.call` 호출 수가 0인지 확인한다. 호출을 끝내도 하위 통신을 계속하는 구현은 열린 연결 수로 검출한다. HTTP 클라이언트와 실행기를 요청마다 새로 만들어 남기지 마라. 서비스 측 계측을 고치거나 요청 종류별 응답을 하드코딩해서는 안 된다.

[9] 지표는 `requests`(회복 요청을 제외한 부하 수), `correct`(상태·본문 일치 수), `within_bound`(4500ms 이내 수), `mismatch_mask`(응답이 틀린 요청 종류), `recovered`(정상 회복 성공 수), `peak_thread_growth`, `end_thread_growth`, `active_calls`, `upstream_seen`(도착 수가 충족된 종류 수), `pending_upstreams`(프록시·하위 서버의 열린 연결 수)이다. `mismatch_mask`는 부하에서 상태·본문이 틀리거나 통신 오류가 난 종류마다 `fast=1`, `connect=2`, `headers=4`, `body=8`, `drop=16`을 한 번씩 더한 값이다. 예를 들어 6이면 connect와 headers에서 불일치가 발생했다. 0이면 모든 종류의 응답이 맞다. 합격 조건은 `correct = within_bound = requests`, `mismatch_mask = 0`, `recovered = upstream_seen = 5`, `peak_thread_growth ≤ 40`, `end_thread_growth ≤ 16`, `active_calls = pending_upstreams = 0`이다. 시간 제한·연결 끊김은 실험 대상이므로 해당 오류 응답도 `correct`에 포함한다.

[10] 수정 범위는 `src/main/kotlin/Gateway.kt`뿐이다. `Server.kt`의 API·계측, 빌드·실행 파일은 바꾸지 마라. `Gateway.call(kind, token)`은 `Reply(status, body)`를 반환한다. 실행기는 서비스를 직접 시작하고 준비 확인·부하·종료를 맡는다. `hidden/`의 채점 구현을 보지 말고 공개 계약으로 구현해라.

[11] 근거를 읽고 타임아웃이 어디까지 덮는지 확인해라. [JDK 21 HttpClient.Builder.connectTimeout](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.Builder.html#connectTimeout(java.time.Duration))은 새 연결 수립에 적용되고 재사용 연결에는 적용되지 않는다. [HttpRequest.Builder.timeout](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html#timeout(java.time.Duration))과 [HttpClient.sendAsync의 취소 계약](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html#sendAsync(java.net.http.HttpRequest,java.net.http.HttpResponse.BodyHandler))을 함께 읽어라. 본문 수신까지 끝나는지는 BodyHandler와 구현의 동작을 확인해야 한다. 이 실험은 헤더 뒤의 느린 본문을 별도로 검증한다. [OpenJDK 21 AsyncSSLConnection.finishConnect](https://github.com/openjdk/jdk21u/blob/master/src/java.net.http/share/classes/jdk/internal/net/http/AsyncSSLConnection.java)는 TLS 완료 뒤 연결 수립을 완료한다.

[12] [Amazon Builders' Library: Timeouts, retries, and backoff with jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/)는 연결·요청 제한을 함께 둘 것과 DNS·TLS 같은 구간이 제한 밖에 남지 않는지 확인할 것을 설명한다. 운영 환경의 제한값은 지연 분포와 허용할 오탐 비율로 정한다. 이 단계의 수치는 로컬 장애를 분리해 관찰하기 위한 실험값이며 운영 권장값이 아니다.
