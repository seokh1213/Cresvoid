# 프레임워크 심화와 튜닝

Spring Boot 와 그 아래 라이브러리(HikariCP, JDBC 드라이버, Tomcat, Hibernate, HTTP 클라이언트, 스레드 풀)의 고급 기능, 엣지 케이스, 숨은 튜닝 지점을 실험으로 체험한다. "이 설정을 바꾸면 부하에서 무엇이 달라지는가" 를 예측하고 확인하는 힘을 기른다.

부하 상황의 일반 엣지 케이스는 [../load/README.md](../load/README.md) 에 있다. 이 문서의 단계도 같은 `lab` 종류([../lab.md](../lab.md))를 쓴다.

## 1. 단계 형식

lab 종류의 세 모드를 쓴다.

| 모드 | 학습자가 하는 일 | 예 |
|---|---|---|
| observe | 준비된 실험을 실행하기 전에 결과를 예측하는 문항을 풀고, 실행한 뒤 예측과 관측값의 차이를 확인한다 | REQUIRES_NEW 와 풀 교착, 트랜잭션 프록시 함정 |
| tune | 설정이 잘못된 서비스를 받아 정해진 파일의 설정과 코드만 바꿔 목표 지표를 맞춘다 | OSIV 와 풀 고갈, HTTP 클라이언트 풀 |
| implement | 핵심 코드만 비운 서비스를 받아 채운다 | 커밋 뒤에 잠금 풀기, 배치 삽입 |

- observe 모드의 채점은 문항이다. 실험 실행은 예측을 관측값과 맞춰 보기 위한 것이고 통과 조건이 아니다. 엔진은 실험을 실행하고 관측값을 보여 준다.
- tune 모드의 `editable` 에는 설정 파일과 허용한 코드 파일만 넣는다. 풀 크기를 무한히 키워 통과하는 길은 불변식으로 막는다(예: 커넥션 수 상한).

## 2. 실행 방식

두 가지를 쓴다. 실험이 요구하는 것보다 무거운 쪽을 고르지 않는다.

| 방식 | 언제 | 메모 |
|---|---|---|
| Kotlin 스크립트(`experiment.main.kts`) | 웹 서버나 부하가 필요 없는 실험. HikariCP 와 H2, spring-jdbc 와 spring-tx, 스레드 풀, Jackson | `@file:DependsOn("그룹:아티팩트:버전")` 으로 Maven Central 에서 받는다. 엔진은 `kotlin experiment.main.kts` 로 실행한다 |
| Gradle Kotlin DSL 프로젝트(`build.gradle.kts`) | Spring Boot 웹 서비스에 k6 부하를 넣는 실험 | Gradle 이 설치되어 있지 않다. Gradle 래퍼를 프로젝트에 둘지 사용자와 정한다 |

- 스크립트에서 Spring 프록시를 쓰려면 클래스와 메서드에 `open` 을 직접 붙인다. 스크립트에는 Gradle 의 kotlin-spring(all-open) 플러그인이 없다. 이 사실 자체가 L-spring-30 의 문항 거리다.
- DB 는 H2 메모리 모드로 시작한다. 느린 쿼리는 H2 의 `CREATE ALIAS` 로 `Thread.sleep` 을 함수로 등록해 만든다. 잠금 대기, 소켓 타임아웃, 서버 쪽 연결 종료처럼 실제 DB 동작이 필요한 실험은 Docker 의 PostgreSQL 이나 MySQL 을 쓴다.
- 서버 쪽 연결 종료와 네트워크 지연은 Kotlin 으로 만든 작은 TCP 프록시로 흉내 낸다. 일정 시간 놀고 있는 연결을 끊거나 지연을 넣는다. 외부 도구를 늘리지 않는다.
- 엔진 자체는 계속 무의존이다. Maven 의존성은 이 트랙의 실험 코드에만 쓴다.

### 사용자가 정할 것

1. 이 트랙의 실험 코드에 Maven 의존성(Spring Boot, HikariCP, H2, Hibernate, JDBC 드라이버)을 허용할지. 허용 범위와 버전 고정 방식(스크립트와 build.gradle.kts 에 버전 명시)을 decisions.md 에 적는다.
2. Gradle 래퍼를 둘지, IntelliJ 의 Gradle 로만 실행할지.
3. Docker 로 DB 와 Kafka 를 띄울지.

## 3. 주제 지도

항목마다 체험할 엣지 케이스나 튜닝 지점을 적었다. 기본값은 2026-10-08 에 문서로 확인한 값이다. 버전에 따라 달라지므로 카드를 만들 때 그 버전의 문서로 다시 확인한다.

### 3.1 커넥션 풀과 DB 연결

- HikariCP 기본값: `maximumPoolSize` 10, `minimumIdle` 은 `maximumPoolSize` 와 같음(고정 크기 풀 권장), `connectionTimeout` 30초, `idleTimeout` 10분, `maxLifetime` 30분, `keepaliveTime` 2분, `leakDetectionThreshold` 0(꺼짐), `validationTimeout` 5초.
- 풀 크기: 작은 풀이 큰 풀보다 빠를 수 있다. 시작점은 (코어 수 × 2) + 유효 스핀들 수다.
- 풀 교착: 한 스레드가 커넥션 두 개를 동시에 쓰면(REQUIRES_NEW, 트랜잭션 안의 별도 커넥션 조회) 풀이 교착한다. 최소 크기는 Tn × (Cm − 1) + 1 이다.
- `maxLifetime` 은 DB 나 네트워크 장비가 연결을 끊는 시간보다 몇 초 짧아야 한다. 더 길면 끊긴 연결을 빌려주게 된다.
- `connectionTimeout` 과 요청 전체 타임아웃의 관계: 풀 대기가 요청 타임아웃보다 길면 클라이언트는 이미 떠났는데 서버는 기다린다.
- JDBC 드라이버의 소켓 타임아웃은 드라이버 기본값이 무한대인 경우가 많다. 응답 없는 DB 가 스레드를 영원히 점유한다.
- OSIV(`spring.jpa.open-in-view`)는 Spring Boot 에서 기본으로 켜져 있고 시작할 때 경고를 남긴다. 켜져 있으면 요청이 끝날 때까지 커넥션을 점유한다. 컨트롤러에서 느린 외부 호출을 하면 풀이 바닥난다.
- 읽기 전용 트랜잭션을 복제본으로 보내는 라우팅은 복제 지연 때문에 방금 쓴 값을 못 읽는 문제를 만든다.

### 3.2 트랜잭션

- 프록시 기반이라 같은 클래스 안의 호출(self-invocation)에는 `@Transactional` 이 적용되지 않는다. private 메서드도 마찬가지다.
- 기본으로 unchecked 예외에서만 롤백한다. checked 예외는 `rollbackFor` 가 없으면 커밋한다.
- 안쪽 REQUIRED 메서드가 예외로 롤백 전용 표시를 남기고 바깥에서 예외를 잡으면 바깥 커밋 때 `UnexpectedRollbackException` 이 난다.
- REQUIRES_NEW 는 바깥 트랜잭션을 멈추고 새 커넥션을 쓴다. NESTED 는 같은 커넥션의 세이브포인트를 쓴다.
- 잠금 해제나 메시지 발행처럼 커밋 뒤에 해야 할 일은 `TransactionSynchronization.afterCommit` 이나 `@TransactionalEventListener` 로 미룬다.
- 트랜잭션 타임아웃과 쿼리 타임아웃은 다르다.

### 3.3 스레드와 실행기

- Tomcat 요청 스레드 풀과 Hikari 풀의 크기 관계. 스레드가 커넥션보다 훨씬 많으면 대부분이 커넥션을 기다린다.
- Spring Boot 가 자동 구성하는 `@Async` 실행기는 가상 스레드를 켜지 않았을 때 `ThreadPoolTaskExecutor` 이고 core 8, max 와 queue 가 사실상 무한이다. `ThreadPoolExecutor` 는 큐가 찰 때만 core 를 넘겨 스레드를 늘리므로 max 를 키워도 스레드는 8개에서 늘지 않는다.
- `@Scheduled` 의 기본 스케줄러는 스레드 하나다. 오래 걸리는 작업 하나가 다른 작업을 모두 늦춘다.
- 가상 스레드(`spring.threads.virtual.enabled`)를 켜면 스레드 수 대신 커넥션 풀과 하위 서비스가 병목이 된다. 동시 실행 수를 세마포어로 묶어야 한다. JDK 21 에서는 `synchronized` 블록 안의 블로킹이 운반 스레드를 고정(pinning)한다. 이 제약은 JDK 24 에서 풀렸다.
- `CompletableFuture.supplyAsync` 에 실행기를 주지 않으면 공용 ForkJoinPool 을 쓴다. 블로킹 작업이 공용 풀을 막는다.
- 스레드 풀에서 ThreadLocal(MDC 포함)을 지우지 않으면 다음 작업에 값이 샌다.

### 3.4 HTTP 클라이언트

- Apache HttpClient 의 `PoolingHttpClientConnectionManager` 기본값은 경로(호스트)마다 5, 전체 25 다. 하위 서비스 하나를 부르는 서비스는 동시 호출이 5개로 묶인다.
- 응답 본문을 다 읽거나 닫지 않으면 커넥션이 풀에 반환되지 않는다.
- 타임아웃은 연결, 풀에서 커넥션을 빌리는 대기, 응답 대기로 나뉜다. 풀 대기 타임아웃이 없으면 요청이 끝없이 줄 선다.
- Reactor Netty(WebClient)의 커넥션 풀과 대기 줄 한도는 버전에 따라 기본값이 다르다. 쓰는 버전의 참조 문서로 확인한다.
- 서버가 먼저 닫은 keep-alive 연결을 재사용하면 첫 요청이 실패한다. 놀고 있는 연결의 수명을 서버보다 짧게 둔다.
- JVM 의 DNS 캐시 TTL 때문에 하위 서비스 주소가 바뀐 뒤에도 옛 주소로 간다.

### 3.5 JPA 와 Hibernate

- N+1 쿼리는 부하에서 쿼리 수와 커넥션 점유 시간을 함께 늘린다. 배치 조회 크기(`default_batch_fetch_size`)와 fetch join 을 비교한다.
- 컬렉션 fetch join 과 페이징을 함께 쓰면 Hibernate 가 경고를 남기고 메모리에서 페이징한다.
- `IDENTITY` 키 생성 전략은 JDBC 삽입 배치를 끈다.
- 큰 일괄 작업에서 영속성 컨텍스트가 커지면 flush 와 dirty checking 이 느려진다. 주기적으로 flush 와 clear 를 한다.
- `@Version` 낙관적 잠금 충돌은 부하에서 늘어난다. 재시도 위치를 트랜잭션 밖에 둔다.

### 3.6 캐시 추상화와 Redis 클라이언트

- `@Cacheable` 도 프록시라 self-invocation 에 적용되지 않는다.
- `@Cacheable(sync = true)` 는 같은 키의 동시 계산을 한 프로세스 안에서 하나로 묶는다. 인스턴스 사이에서는 묶지 않는다.
- null 결과를 캐시할지, 캐시별 TTL, 직렬화 방식(JDK 직렬화와 JSON)을 정한다.
- Lettuce 는 스레드 안전한 연결 하나를 공유한다. 풀은 블로킹 명령이나 트랜잭션처럼 연결을 독점하는 경우에만 필요하다.

### 3.7 웹과 직렬화

- Jackson 의 알 수 없는 필드 처리, 날짜와 시간대 직렬화, `BigDecimal` 표현.
- 우아한 종료(`server.shutdown=graceful`)가 없으면 배포 때 처리 중인 요청이 끊긴다.
- 요청 크기 제한과 업로드 처리.

### 3.8 Kafka(spring-kafka)

- 처리 시간이 `max.poll.interval.ms` 를 넘으면 재조정이 일어나고 커밋 전 메시지가 다시 온다.
- 리스너 동시성이 파티션 수보다 크면 남는 스레드는 논다.
- 오류 처리기, 재시도, DLT, 커밋 모드에 따라 중복과 유실이 달라진다.
- 브로커가 필요하므로 Docker 결정 뒤에 다룬다. Kafka 자체의 동작은 [../messaging/README.md](../messaging/README.md) 에서 다룬다.

### 3.9 JVM

- 컨테이너 메모리 한도와 `MaxRAMPercentage`, GC 선택, 스레드 스택 크기.
- JIT 예열 전의 측정값은 낮게 나온다. 부하 시험에 예열 구간을 둔다.
- 의존성 없이 JDK 만으로 하는 JVM 실험은 [../jvm/README.md](../jvm/README.md) 에 있다.

## 4. 카드

트랙은 `spring-lab`(종류 lab)이다. 카드는 `cards/` 에 한 장씩 있다. '실행 방식' 절의 "사용자가 정할 것" 이 정해져야 만들 수 있다.
