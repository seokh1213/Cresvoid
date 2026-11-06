# N+1과 페이징

[1] 이 단계는 목록 API를 고치는 tune 실험이다. 시작 코드의 목록 응답이 맞더라도 페이지 크기를 늘렸을 때 SQL 수가 어떻게 달라지는지 관찰한 뒤 수정해라. 실제 Spring Boot·JPA·H2를 쓰며, `build.sh`는 기존 실행 이미지 안에서 실행 파일 `build/app.jar`를 만든다. `run.sh`는 `PORT` 환경 변수로 받은 포트를 호스트의 루프백 주소에 공개한다. Docker Desktop을 켠 뒤 실행해라. Gradle 파일은 IDE용이며 빌드 중 라이브러리를 새로 내려받지 않는다.

[2] 먼저 `./build.sh`로 빌드하고 `PORT=18080 ./run.sh`로 서비스를 띄워라. `/health`가 HTTP 200과 `ready`를 반환할 때까지 기다려라. `GET /orders?page=0&size=7`과 `GET /orders?page=0&size=40`의 본문과 `GET /metrics`의 변화를 비교해라. 수정 후 같은 절차와 실험을 반복해라. 수동 실행을 끝내면 Ctrl+C로 서비스를 종료해라. 런타임 진입점은 `lab.MainKt`이며 루트 `Main.kt`는 공통 실행 스크립트를 위한 전달 함수다.

[3] 공개 목록 계약은 `GET /orders?page=P&size=S`다. P는 0부터 1000, S는 1부터 40인 정수다. 유효한 요청에는 HTTP 200과 UTF-8 탭 구분 텍스트를 반환해라. 첫 줄은 `TOTAL<TAB>전체 주문 수`, 이후 각 줄은 `주문 ID<TAB>상태<TAB>쉼표로 이은 품목 ID`다. 모든 줄은 LF로 끝난다. 주문은 ID 오름차순이며 P×S개를 건너뛰고 최대 S개를 반환해라. 주문별 품목은 하나도 빠짐없이 ID 오름차순으로 담아라. 품목이 없으면 세 번째 필드를 비워 두되 마지막 탭을 유지해라. 범위를 벗어난 페이지는 TOTAL 줄만 반환해라. 잘못된 정수 범위에는 HTTP 400을 반환하는 기존 처리를 유지해라.

[4] 시작 데이터는 주문 ID 1부터 137이다. 주문 ID가 3의 배수면 상태는 CLOSED, 나머지는 OPEN이다. ID가 i인 주문에는 i를 5로 나눈 나머지만큼 품목이 있고, 품목 ID는 10×i+1부터 연속이다. 예를 들어 `page=1&size=1`의 본문은 `TOTAL<TAB>137<LF>2<TAB>OPEN<TAB>21,22<LF>`다. `POST /admin/append`는 ID가 현재 최댓값보다 1 큰 주문을 같은 규칙으로 추가하고 새 ID와 LF를 반환한다. 추가가 끝난 뒤의 모든 목록 응답은 갱신된 total과 항목을 반환해야 한다. 이 실험은 추가 요청과 목록 부하를 겹치지 않게 보낸다.

[5] 시나리오마다 서비스를 새로 한 번 띄운다. `catalog-browse`는 초기 데이터를 유지한다. `catalog-refresh`는 첫 목록 조회 뒤 주문 두 개를 추가한다. 두 시나리오 모두 준비 확인 뒤 `page=0&size=20` 한 건을 보내 예열하고, 이후 초당 12건을 4초 동안 예정된 시각에 보낸다. 앞선 응답을 기다려 다음 요청을 늦추지 않는다. 부하의 48건은 (P,S)=(0,1),(1,7),(3,20),(0,40),(3,40),(6,20),(19,7),(137,1),(200,7),(0,20)을 차례로 반복한다. 각 HTTP 요청의 제한 시간은 15초다. 예열 한 건과 부하 48건의 목록 응답을 모두 판정하며 정상 목록 요청 수는 49건이다. 예열의 total은 137이다. catalog-browse의 부하는 total=137, catalog-refresh의 추가 후 부하는 total=139를 기준으로 비교한다.

[6] 각 목록 요청이 준비한 SQL 문 수는 1개 이상 3개 이하여야 한다. 초기 적재·추가·준비 확인·지표 조회의 SQL은 제외하고 Hibernate Statistics의 `prepareStatementCount` 증분을 센다. 각 요청은 새 영속성 컨텍스트에서 조회하며 2차 캐시와 쿼리 캐시는 꺼져 있다. 계측 코드가 목록 처리 구간을 한 번에 하나씩 실행해 전역 통계가 다른 요청과 섞이지 않게 한다. 이 직렬 계측 구간은 SQL 수를 재기 위한 장치다. 스레드 처리량이나 OSIV를 튜닝하는 단계로 해석하지 마라.

[7] 모든 판정 대상 목록 응답은 HTTP 200이어야 하며 TOTAL·페이지 경계·상태·품목 ID가 데이터와 정확히 일치해야 한다. 메모리 페이징 경고 수는 0이어야 한다. 요청별 `entityLoadCount` 증분은 그 응답의 주문 수와 품목 수를 더한 값 이하여야 한다. 빈 페이지는 엔티티를 로드하지 않아야 한다. 전체 엔티티를 읽고 메모리에서 잘라 내거나 로그만 숨겨도 이 로드 한도를 넘으면 실패한다. 캐시·상수 응답으로 DB 조회를 생략하거나 데이터·계측을 바꾸지 마라.

[8] 실험은 최대 10개 지표를 출력한다. `requests`는 처리된 목록 요청 수, `response_errors`는 HTTP 실패와 요청 수 불일치, `content_errors`는 목록 및 추가 응답의 본문 불일치 수다. `sql_per_request_min`과 `sql_per_request_max`는 요청별 SQL 수의 최솟값과 최댓값, `sql_per_request_mean`은 전체 SQL 수를 목록 요청 수로 나눈 값이다. `paging_warnings`는 Hibernate 메모리 페이징 경고 수, `overfetches`는 [7]의 엔티티 로드 한도를 넘긴 요청 수, `entities_per_request_max`는 요청별 엔티티 로드 수의 최댓값이다. `p95_ms`는 예열을 제외한 부하 요청 48건의 응답 시간 95백분위수다. p95는 관찰용이며 시간 임계값으로 합격을 판정하지 않는다. HTTP 실패·본문 불일치·경고·과다 로드가 모두 0이고 [5]~[7]을 만족해야 합격이다.

[9] 수정 범위는 `src/main/kotlin/lab/Catalog.kt`뿐이다. 이 파일 안에 조회용 함수와 DTO를 더해도 된다. `Catalog.page(page, size): OrderPage` 계약과 읽기 전용 트랜잭션 경계를 유지해라. 엔티티·고정 데이터·컨트롤러·계측·진입점·실행 스크립트·빌드 설정·라이브러리 묶음은 바꾸지 마라. 저장소에 이미 있는 Spring·JPA·H2 외에 의존성을 더하지 마라. SQL 개수와 로드할 데이터 범위를 함께 검토해라.

[10] 품목이 없는 주문도 결과에 남아야 한다. 정렬과 빈 페이지, 마지막 페이지에서도 같은 계약을 지켜라. 수정 전후에 SQL 수뿐 아니라 응답 내용과 엔티티 로드 수도 비교해라.

[11] 이 실행 이미지는 Hibernate 7.4 계열과 H2를 사용한다. 같은 조회 구문도 Hibernate 버전과 DB에 따라 페이징 동작이 달라질 수 있다. 이전 버전의 경험만으로 판정하지 말고 이 환경의 실제 SQL·로드 개수·경고와 응답을 확인해라. 구체적인 버전 차이와 공식 근거는 아래 관찰 후 힌트에 있다.

[12] 쿼리 계측 API의 의미는 [Hibernate Statistics](https://docs.hibernate.org/orm/7.4/javadocs/org/hibernate/stat/Statistics.html#getPrepareStatementCount())와 [entityLoadCount](https://docs.hibernate.org/orm/7.4/javadocs/org/hibernate/stat/Statistics.html#getEntityLoadCount())를 참고해라. 준비된 SQL 문 수는 JPQL 호출 횟수와 같지 않을 수 있다. 저장소 메서드에 쿼리를 선언할 때의 공식 계약은 [Spring Data JPA Query Methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html)를 참고해라.

<details>
<summary>관찰 후 힌트: 조회 방식과 버전 차이</summary>

[13] [2]의 관찰을 마친 뒤 필요하면 이 힌트를 읽어라. 지연 로딩된 컬렉션을 주문마다 열면 추가 SELECT가 주문 수에 비례해 늘어날 수 있다. 조회 범위를 제한한 뒤 필요한 컬렉션을 한꺼번에 읽는 방법과 DB에서 조인 결과를 제한하는 방법을 검토해라. 조인된 행 수와 주문 수는 다르다는 점에 주의해라.

[14] Hibernate 7.4는 지원 DB에서 컬렉션 fetch join과 limit/offset을 SQL로 변환한다. 따라서 컬렉션 fetch join을 썼다는 사실만으로 메모리 페이징이라고 단정하지 마라. 이전 버전은 같은 조합에서 메모리 페이징을 했으며, 7.4에서도 `org.hibernate.limitInMemory` 힌트는 이전 동작을 복원한다. 근거는 [Hibernate 7.4 변경 사항](https://docs.hibernate.org/orm/7.4/whats-new/#limit-fetch-join)과 [HINT_LIMIT_IN_MEMORY API](https://docs.hibernate.org/orm/7.4/javadocs/org/hibernate/jpa/HibernateHints.html#HINT_LIMIT_IN_MEMORY)다.

</details>
