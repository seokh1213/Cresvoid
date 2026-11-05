# lab 실험 환경

lab 단계의 실험 스크립트가 함께 쓰는 도구와 고정 이미지다. 계약의 요약은 `content/FORMAT.md` 5.1 절에 있다.

## 파일

| 파일 | 내용 |
|---|---|
| `load.main.kts` | 개방형 부하(`steady`, `runLoad`), 학습자 프로그램 시작과 준비 확인(`Program`), 빈 포트, 백분위수, METRIC 출력 |
| `docker.main.kts` | 고정 이미지 목록 `LAB_IMAGES`, Docker 확인(`requireDocker`), compose 실행 단위 `ComposeStack` |
| `kotlin/RedisClient.kt` | JDK 만으로 만든 RESP2 Redis 클라이언트. 시작 코드와 참조 풀이에 그대로 복사한다 |
| `images/elasticsearch-nori/` | 고정한 Elasticsearch 위에 한글 분석기 analysis-nori 를 얹는 Dockerfile |
| `images/jvm-runner/` | JDK 21, Kotlin 2.4.21, 버전을 고정한 라이브러리 묶음(`libs/<묶음>/pom.xml`)을 담은 실행 이미지 |
| `jvm-runner.sh` | lab 프로젝트의 `build.sh`·`run.sh`가 부르는 진입점. 실행 이미지를 처음 한 번 만들고 그 안에서 빌드·실행한다 |

## 쓰는 법

실험 스크립트 맨 위에서 필요한 도구를 불러온다. 경로는 `hidden/` 기준이다.

```kotlin
@file:Import("../../../../../lab-env/docker.main.kts")
@file:Import("../../../../../lab-env/load.main.kts")
```

외부 서비스가 필요하면 `hidden/compose.yaml`을 둔다.

```yaml
services:
  redis:
    image: ${REDIS_IMAGE}
    ports: ["127.0.0.1::6379"]
    command: ["redis-server", "--save", "", "--appendonly", "no"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 1s
      timeout: 1s
      retries: 30
    deploy:
      resources:
        limits: { cpus: "1", memory: 256m }
```

`ComposeStack(File("compose.yaml"), "<트랙>-<단계>").use { stack -> stack.up(); ... }` 로 띄운다. `stack.port("redis", 6379)` 로 열린 포트를 찾아 학습자 프로그램의 환경 변수로 넘긴다.

### Elasticsearch 와 ClickHouse

둘 다 HTTP 로 다루므로 학습자 코드는 JDK `HttpClient` 만 쓴다. Elasticsearch 는 nori 이미지를 compose 가 처음 한 번 빌드한다.

```yaml
services:
  elasticsearch:
    build:
      context: ../../../../../lab-env/images/elasticsearch-nori
      args: { BASE: "${ELASTICSEARCH_IMAGE}" }
    image: cresvoid-lab/elasticsearch-nori:9.5.5
    environment:
      discovery.type: single-node
      xpack.security.enabled: "false"
      ES_JAVA_OPTS: "-Xms512m -Xmx512m"
    ports: ["127.0.0.1::9200"]
    healthcheck:
      test: ["CMD-SHELL", "curl -fs 'localhost:9200/_cluster/health?wait_for_status=yellow&timeout=1s' >/dev/null"]
      interval: 2s
      timeout: 3s
      retries: 60
    deploy:
      resources:
        limits: { cpus: "2", memory: 1536m }
  clickhouse:
    image: ${CLICKHOUSE_IMAGE}
    environment:
      CLICKHOUSE_SKIP_USER_SETUP: "1"
    ports: ["127.0.0.1::8123"]
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- localhost:8123/ping >/dev/null"]
      interval: 1s
      timeout: 2s
      retries: 60
    deploy:
      resources:
        limits: { cpus: "2", memory: 1g }
```

ClickHouse 26 은 기본 사용자에 비밀번호를 요구한다. 127.0.0.1 에만 여는 실습용이라 `CLICKHOUSE_SKIP_USER_SETUP`으로 비밀번호 없이 쓴다. Elasticsearch 는 처음 시작에 20초 안팎 걸리므로 `stack.up(240)`처럼 기다림을 넉넉히 준다.

### 라이브러리가 필요한 학습자 코드

JDBC 드라이버, Kafka 클라이언트, Spring 이 필요한 lab 은 학습자 프로젝트를 실행 이미지 안에서 빌드하고 실행한다. 학습자 기기에는 라이브러리를 내려받지 않고 저장소에도 jar 를 넣지 않는다.

- 프로젝트 루트의 `.lab-libs` 파일에 묶음 이름 하나를 적는다. 묶음은 `jdbc-mysql`, `jdbc-postgres`, `kafka`, `spring`, `spark` 다.
- Spark 는 클러스터 없이 로컬 모드로 쓴다. 학습자 코드가 `SparkSession.builder().master("local[2]").config("spark.ui.enabled", "false")`로 세션을 만들고 SQL 이나 Dataset 작업을 실행한다. `spark-submit`과 `spark-sql` 대신 같은 JVM 에서 실행하므로 결과가 기기마다 덜 달라진다.
- `.lab-libs` 가 있는 프로젝트는 `kotlin content/templates/sync.main.kts` 가 `content/templates/kotlin-lab`의 `build.sh`·`run.sh`·`build.gradle.kts`를 복사한다. 두 스크립트는 `jvm-runner.sh`로 넘기고, Gradle 파일은 IDE 용으로 진입점을 `lab.MainKt`로 둔다.
- 코드는 `lab` 패키지에 두고 진입점은 `lab.MainKt`다. Spring 은 기본 패키지를 스캔하지 못한다.
- 실험에서는 학습자 앱도 compose 서비스로 띄운다. `ComposeStack(File("compose.yaml"), "<트랙>-<단계>", programEnvironment(File(args[0])))`가 `${PROGRAM_DIR}`을 넘긴다.

```yaml
  app:
    build: ../../../../../lab-env/images/jvm-runner
    image: ${JVM_RUNNER_IMAGE}
    volumes: ["${PROGRAM_DIR}:/work:ro"]
    working_dir: /work
    command: ["lab-run", "spring", "lab.MainKt"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/lab
    ports: ["127.0.0.1::8080"]
    depends_on:
      mysql: { condition: service_healthy }
    healthcheck:
      test: ["CMD-SHELL", "curl -fs localhost:8080/health >/dev/null"]
      interval: 1s
      timeout: 2s
      retries: 90
```

스스로 끝나는 클라이언트(생산자, 일괄 작업)는 `profiles: ["client"]`로 두고 `stack.run("app")`으로 한 번 실행해 출력을 읽는다.

MySQL 은 `MYSQL_ROOT_PASSWORD`·`MYSQL_DATABASE`와 `mysqladmin ping` 준비 확인, PostgreSQL 은 `POSTGRES_PASSWORD`와 `pg_isready`, Kafka 는 KRaft 단일 노드 설정(`KAFKA_PROCESS_ROLES: broker,controller`, `KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092` 등)과 `kafka-broker-api-versions.sh` 준비 확인을 쓴다. 복제를 다루는 실험은 브로커를 셋 두고 메모리 한도를 낮춘다.

## 규칙

- 이미지는 `LAB_IMAGES`에 digest 로 고정한 것만 쓴다. 새 이미지는 버전과 digest 를 정하고 이 파일과 `docker.main.kts`에 함께 더한다.
- compose 서비스마다 healthcheck 와 CPU·메모리 한도를 둔다. 한도는 학습자 기기마다 결과가 달라지는 것을 줄인다.
- 포트는 임의 포트로 연다. 실행마다 프로젝트 이름이 달라 여러 실험이 동시에 돌아도 겹치지 않는다.
- 부하는 개방형 모델로 보낸다. 느려진 서비스가 도착률을 낮추지 않아야 과부하, 재시도, 스탬피드가 드러난다.
- 판정은 요청 수, 원천 호출 수, 동시 실행 상한처럼 관계와 개수로 한다. 지연 시간 임계값은 Docker Desktop 의 가상 머신을 거치므로 여유를 크게 둔다.
- Docker 가 꺼져 있으면 실험 오류다. 학습자에게 Docker Desktop 을 켜라고 알린다.

## 고정 이미지

| 변수 | 이미지 | 버전 |
|---|---|---|
| `REDIS_IMAGE` | `redis@sha256:21090af93f8324db2f0f0f4f38a7c60369d47d917e000c3423d89d2ae2883537` | 8.10.3 alpine |
| `ELASTICSEARCH_IMAGE` | `elasticsearch@sha256:764a939891119d97e130b4d5da196c330afb2338382ada9e1b9b7943ce6707df` | 9.5.5. nori 이미지의 기반 |
| `CLICKHOUSE_IMAGE` | `clickhouse/clickhouse-server@sha256:d426d2f612e1e189c90f9a3f8dcd85a03c6851273833817a296bb327e63aa7a7` | 26.8 LTS |
| `MYSQL_IMAGE` | `mysql@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902` | 8.4.13 LTS |
| `POSTGRES_IMAGE` | `postgres@sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5` | 18.6 alpine |
| `KAFKA_IMAGE` | `apache/kafka@sha256:833b867a9585a141da455dc2b57431c65cbccb27f11c91f694444cca9f777e0f` | 4.4.0 |
| `JVM_RUNNER_IMAGE` | `cresvoid-lab/jvm-runner:2` | `images/jvm-runner`에서 로컬로 만든다. 그 디렉터리를 바꾸면 태그도 함께 올린다 |

실행 이미지의 라이브러리 묶음은 Spring Boot 4.1.2(Spring 7.0.10, Hibernate 7.4), MySQL Connector/J 26.10.0, PostgreSQL JDBC 42.7.14, kafka-clients 4.4.0, HikariCP 7.1.0, H2(Spring Boot 관리 버전), Spark SQL 4.3.0(Scala 2.13)이다. Kotlin 런타임은 컴파일러와 같은 2.4.21 로 맞춘다.
