# OSIV 와 느린 외부 호출

[1] 풀 크기 10을 유지하면서 느린 외부 호출과 데이터베이스 조회가 함께 있는 API를 개선해라. 시작 코드를 빌드하고 먼저 실험해라. 낮은 도착률과 높은 도착률의 p95, 풀 대기, 외부 호출 중 연결 보유 횟수를 비교한 뒤 수정하고 다시 실험해라. 지연이 줄었더라도 응답 데이터가 빠졌거나 외부 호출을 생략했다면 완료한 것이 아니다.

[2] JDK 21과 기존 실행 이미지의 spring 라이브러리 묶음을 사용한다. .lab-libs를 유지하고 lab 패키지에서 구현해라. 프로젝트 루트에서 CRESVOID_ROOT를 저장소 절대 경로로 지정하고 ./build.sh로 build/app.jar를 만들어라. PORT=18082 ./run.sh는 루프백의 해당 포트로 서비스를 공개한다. Docker Desktop을 켜고 실행해라. 실험은 같은 이미지를 compose로 한 번 띄우고 준비 확인, 예열, 두 부하 구간, 지표 수집, 종료를 수행한다. 이 단계는 이미지 안의 Kotlin 컴파일 경로를 사용하며 Gradle 의존성 다운로드와 외부 부하 도구는 사용하지 않는다.

[3] GET /health는 초기 데이터가 준비된 뒤 HTTP 200과 ready를 반환한다. GET /purchases/{id}는 1부터 40까지의 id를 받으며 HTTP 200으로 id, owner, items, external 필드를 가진 JSON을 반환한다. id가 7이면 {"id":7,"owner":"reader-7","items":["book-7","pen-7"],"external":"checked-7"}이다. 모든 id에 같은 규칙이 적용되며 items의 순서를 유지해라. 각 요청은 해당 Purchase를 실제 H2에서 조회하고 SlowExternal.call(id)를 정확히 한 번 호출해야 한다. 호출 결과를 캐시하거나 응답 데이터를 상수로 대체하지 마라.

[4] Purchase.items는 지연 로딩 컬렉션이다. SlowExternal.call(id)는 300ms 걸리는 가짜 외부 호출이다. spring.jpa.open-in-view는 요청 단위 EntityManager 관리 설정이다. EntityManager가 열려 있는 것과 JDBC 연결을 빌린 것은 서로 다르다. hibernate.connection.handling_mode는 DELAYED_ACQUISITION_AND_HOLD로 고정한다. 이 모드에서 처음 빌린 JDBC 연결은 세션 종료 때 반납된다. 트랜잭션, EntityManager, 물리 연결의 수명을 구분해 관찰해라. 다른 연결 보유 모드에서도 같은 결과가 난다고 일반화하지 마라.

[5] 공통 load.main.kts의 개방형 부하를 사용한다. 8요청/초로 1초 예열하고 지표를 초기화한다. 측정 시작 시각부터 10요청/초로 2초간 20개를 보내고, 시작 3초 뒤부터 60요청/초로 3초간 180개를 보낸다. 앞 요청의 완료를 기다려 다음 요청을 예약하지 않는다. 각 구간에서 id를 1부터 40까지 순환한다. 측정 시작부터 8초까지 50ms 간격으로 /metrics를 조회해 pending의 최댓값을 구한다. HTTP 요청별 제한 시간은 구매 조회 15초, 지표 조회 5초다. p95는 각 구간의 전체 구매 요청에 대해 HTTP 전송 시작부터 응답 본문 수신까지 걸린 시간의 오름차순 ceil(0.95 × 요청 수)번째 값이다. 예열은 합격 판정에서 제외한다.

[6] 측정한 구매 요청 200개가 모두 올바른 본문의 HTTP 200으로 끝나야 한다. 외부 호출 수는 정확히 200이어야 하며 풀 크기는 정확히 10이어야 한다. 외부 호출 시작 시 자기 요청의 JDBC 연결을 보유한 횟수는 0이어야 한다. 부하 종료 후 활성 연결과 풀 대기 요청도 각각 0이어야 한다. 낮은 부하와 높은 부하의 p95가 각각 1500ms 이하여야 한다. 300ms 외부 호출에 비해 여유를 두되, 연결 보유 여부는 시간과 무관하게 횟수로 판정한다. 풀 크기를 늘리는 방법은 이 단계의 제약을 위반한다.

[7] 출력 지표는 requests, bad_responses, external_calls, held_calls, pool_size, active_after, pending_after, pending_peak, low_p95_ms, high_p95_ms의 10개다. bad_responses는 상태나 본문이 계약과 다른 구매 응답 수다. held_calls는 계측용 DataSource가 요청 스레드별로 센 실제 연결 대여 수를 SlowExternal 진입 시 확인한 횟수다. pending_peak는 관찰용이며 순간적인 양수만으로 실패시키지 않는다. GET /metrics는 external_calls, held_calls, pool_size, active, pending을 key=value 형식으로 한 줄씩 반환한다. POST /metrics/reset은 호출 두 카운터를 0으로 초기화하고 reset을 반환한다. 지표 조회와 초기화 실패, 서비스 준비 실패는 불변식 실패와 구분하는 실험 오류다.

[8] 수정할 수 있는 파일은 application.yml과 src/main/kotlin/lab/Service.kt뿐이다. application.yml에서는 spring.jpa.open-in-view만 바꿔라. 풀 크기, 연결 보유 모드, 서버 설정은 유지해라. Service.kt에서는 조회 방식, 반환 자료형, 서비스 구성과 트랜잭션 경계를 바꿀 수 있다. 엔티티, 데이터 초기화, 컨트롤러, DataSource 계측, SlowExternal, 빌드·실행 스크립트, .lab-libs는 바꾸지 마라. 연결을 직접 닫거나 별도 풀을 만들지 마라. 외부 호출을 다른 스레드로 옮겨 연결 계측을 피하지 마라.

[9] 공식 근거는 Spring의 [OpenEntityManagerInViewInterceptor API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/support/OpenEntityManagerInViewInterceptor.html), Hibernate 7.4의 [PhysicalConnectionHandlingMode API](https://docs.hibernate.org/orm/7.4/javadocs/org/hibernate/resource/jdbc/spi/PhysicalConnectionHandlingMode.html), Spring의 [선언적 트랜잭션 문서](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)다. 코드와 관찰 결과를 대조할 때 참고해라.
