# maxLifetime과 끊긴 연결

[1] 상품 조회 서비스의 연결 수명을 관찰하고 조정해라. HikariCP가 H2 TCP 서버에 붙으며, 두 구성 요소 사이의 Kotlin 프록시가 네트워크 장비의 연결 종료를 재현한다. 예측 문항을 먼저 풀고 시작 코드의 지표를 관찰한 뒤 설정을 고쳐라. 실행에는 JDK 21과 기존 spring 라이브러리 묶음을 담은 Docker 실행 이미지를 사용한다. 별도 라이브러리나 부하 도구를 설치하지 마라.

[2] 카드는 20초 유휴 단절과 30초 휴식을 제안하지만 HikariCP는 0이 아닌 maxLifetime과 keepaliveTime에 최소 30,000ms를 요구한다. 이 단계는 실제 공개 API를 사용하도록 유휴 단절을 60초, 휴식을 90초로 늘린다. 프록시는 어느 방향으로든 마지막 바이트가 흐른 뒤 60초가 지나면 연결을 닫는다. 또한 계속 사용하더라도 연결 생성 후 75초가 지나면 닫는다. 프록시는 100ms마다 검사하며 유휴 조건을 먼저 검사한다. 단순한 정상 반납·종료는 강제 단절에 세지 않는다.

[3] 공개 HTTP 계약은 다음과 같다. run.sh는 PORT 환경 변수의 포트에 서비스를 띄우고 호스트에서는 127.0.0.1로만 공개한다. GET /health는 풀에 유휴 연결 4개가 준비된 뒤 200과 ready를 반환한다. GET /query는 풀에서 연결을 빌려 SELECT 42를 한 번 실행하고 자원을 닫은 뒤 200과 42를 반환한다. SQLException이면 503과 database unavailable을 반환하며 재시도하지 않는다. GET /stats는 JDBC를 사용하지 않고 successes, failures, created, idle_drops, age_drops, idle, active, total을 이름=정수 형식으로 한 줄씩 반환한다. successes와 failures는 업무 SQL 요청의 누계다. created는 프록시를 통과한 TCP 연결 누계이며 idle_drops와 age_drops는 각 종료 조건의 누계다. 나머지는 HikariPoolMXBean의 현재 연결 수다.

[4] 실행 전에 시작 설정 유지, keepaliveTime만 변경, maxLifetime만 변경, 두 값 변경 전략을 비교해라. 각 전략의 후보 값을 정하고 idle_drops·age_drops의 발생 여부, connections_created의 예상 범위와 재개 HTTP 요청의 성공 가능성을 적어라. 먼저 시작 설정을 실행한 뒤 선택한 후보도 같은 부하로 관찰하고 예측과 비교해라. HTTP 성공 수와 프록시 단절 수가 다르게 보인 이유, 연결 생성 수가 달라진 이유를 설명하고 최종 설정을 골라라. 쉬고 난 뒤 모든 요청이 반드시 실패한다고 가정하지 마라. HikariCP는 오래 놀던 연결을 빌려줄 때 검증하여 닫힌 연결을 교체할 수 있다. 0이 아닌 maxLifetime이 30,000ms보다 작으면 기본값으로 교정된다. maxLifetime=0은 수명에 따른 폐기를 끈다. 0이 아닌 keepaliveTime이 30,000ms 미만이면 비활성화되며 maxLifetime이 0이 아닐 때 keepaliveTime이 maxLifetime 이상이어도 비활성화된다. maxLifetime은 사용 중인 연결을 강제로 끊지 않고 반납 뒤 폐기하며, keepalive는 풀 안에서 놀고 있는 연결만 검사한다.

[5] evening-traffic은 새 서비스 하나로 실행한다. 준비를 확인하고 기준 통계를 읽은 뒤, 0ms부터 100ms 간격으로 /query 20개를 보낸다. 모두 완료되면 다음 부하의 기준 시각을 잡는다. 그 시각으로부터 89,000ms에 /stats를 한 번 읽고 90,000ms부터 100ms 간격으로 /query 20개를 다시 보낸다. 휴식 동안 업무 요청과 별도 JDBC 호출은 없다. 도착 시각은 이전 응답과 무관한 개방형 부하로 정한다. HTTP 요청 제한은 8초이고 연결 제한은 3초다. 종료 통계를 읽은 뒤 서비스와 Docker 자원을 회수한다. 컴파일 시간을 제외한 실험 제한은 210초다.

[6] initial_ok와 resumed_ok는 각 구간에서 200과 본문 42를 받은 수이며 각각 20이어야 한다. business_errors는 기준 이후 SQL 실패 수이며 0이어야 하고 completed_queries는 같은 기간 SQL 성공 수이며 40이어야 한다. query_p95_ms는 두 구간의 업무 요청 40개의 전송 시작부터 응답 또는 실패까지 걸린 시간에서 오름차순 38번째 값이며 5,000ms 이하여야 한다. 통계 요청은 백분위수에서 제외한다. 이 시간 기준으로 요청이 막히는 회귀를 잡는다.

[7] 프록시의 idle_drops와 age_drops는 모두 0이어야 한다. 두 지표는 기준 통계부터 종료 통계까지의 증가량이다. connections_created는 시작 시점부터 종료까지 프록시가 받은 연결 수이며 4 이상 16 이하여야 한다. pause_idle은 휴식 중 89,000ms에 관찰한 유휴 연결 수이며 4여야 한다. 종료 통계의 total은 4, active는 0이어야 한다. 이 조건으로 끊긴 연결을 나중에 교체해 HTTP 성공만 맞추거나 풀을 없애는 우회를 막는다. 전체 실험은 9개 지표를 출력한다.

[8] 수정 범위는 src/main/kotlin/lab/PoolSettings.kt의 maxLifetime과 keepaliveTime 두 설정값뿐이다. 한 값만 바꾸어도 되며 두 값을 모두 바꿀 필요는 없다. 단절을 막는 것뿐 아니라 연결 생성 수가 허용 범위에 드는지도 확인해라. HikariCP의 공개 설정 API를 사용하고 풀 크기 4, 대기·검증 제한, JDBC 주소와 SQL 처리 방식은 유지해라. Main.kt, IdleProxy.kt, 공통 빌드·실행 파일, 통계와 프록시는 바꾸지 마라. 사용 중인 연결의 강제 종료나 별도 주기 작업으로 수명을 흉내 내지 마라. build.sh는 build/app.jar를 만들며 run.sh는 이를 실행한다. CRESVOID_ROOT가 프로젝트 루트를 가리키는 환경에서 두 스크립트를 실행해라.

[9] 공식 근거는 [HikariCP 설정 문서](https://github.com/brettwooldridge/HikariCP/tree/HikariCP-7.0.2#configuration-knobs-baby)의 maxLifetime·keepaliveTime, [HikariConfig 검증 구현](https://github.com/brettwooldridge/HikariCP/blob/HikariCP-7.0.2/src/main/java/com/zaxxer/hikari/HikariConfig.java)의 validateNumerics, [HikariPool 구현](https://github.com/brettwooldridge/HikariCP/blob/HikariCP-7.0.2/src/main/java/com/zaxxer/hikari/pool/HikariPool.java)의 getConnection·createPoolEntry, [H2 서버 실행 문서](https://www.h2database.com/html/tutorial.html#using_server)다. 이 단계는 실행 이미지에서 확인한 HikariCP 7.0.2와 H2 2.4.240을 사용한다. HikariCP의 수명·keepalive 예약에는 조기 실행을 위한 무작위 편차가 있으므로 정확히 몇 초에 연결이 교체되는지는 정답으로 삼지 마라.
