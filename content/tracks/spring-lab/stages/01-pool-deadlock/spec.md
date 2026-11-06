# REQUIRES_NEW와 풀 교착

[1] 이 단계는 observe 뒤 tune 순서로 진행한다. 시작 코드를 실행하기 전에 풀 크기 N=4, 동시 요청 스레드 Tn=4, 요청당 최대 동시 커넥션 Cm=2일 때의 대기를 예측해라. 바깥 메서드가 커넥션을 얻은 뒤 별도 빈의 REQUIRES_NEW 메서드를 호출한다. connectionTimeout은 2,000ms다. 실제 Spring 프록시, DataSourceTransactionManager, HikariCP, H2를 사용한다.

[2] JDK 21과 제공된 spring 실행 이미지를 사용한다. 프로젝트의 .lab-libs는 spring이며 build.sh로 build/app.jar를 만든다. run.sh는 PORT 환경 변수로 지정한 포트를 사용한다. Docker가 포트를 호스트의 127.0.0.1에만 공개한다. 앱의 진입점은 lab.MainKt이며 기본 템플릿을 위한 기본 패키지의 Main.kt는 위임만 한다. 라이브러리를 설치하거나 버전을 바꾸지 마라.

[3] 서비스 계약은 다음과 같다. GET /health는 준비되면 200과 ready를 반환한다. POST /batch의 본문은 영문·숫자·하이픈으로 된 1~80자 배치 이름이며 참여자는 항상 4명이다. 이전 요청이 끝난 뒤에만 새 배치를 열어라. POST /work?id=<고유한 id>&rollback=<true 또는 false>는 배치당 정확히 4번 호출한다. id는 영문·숫자·하이픈으로 된 1~100자 문자열이다. 각 요청은 Batch.ready()를 정확히 한 번 호출하고 /release를 기다린다. GET /gate는 DB를 조회하지 않고 준비된 요청 수를 정수로 반환한다. POST /release는 네 요청이 모두 준비된 뒤 대기를 푼다. 게이트는 실행 순서를 고정하는 관찰 장치이며 호출 위치는 바꿀 수 있다.

[4] 정상 요청은 audit_log와 orders에 해당 id를 각각 한 행 커밋하고 HTTP 200, 본문 committed를 반환해야 한다. rollback=true 요청은 감사 행을 커밋한 뒤 주문 트랜잭션에서 PlannedRollback을 던진다. 이 요청은 HTTP 200, 본문 rolled_back을 반환하며 감사 행 한 개만 남고 주문 행은 없어야 한다. 감사 커밋은 주문 롤백과 독립적이어야 한다. 각 테이블은 id 기본 키를 사용하며 테스트 id는 모두 다르다. 커넥션 획득 실패는 HTTP 503, 본문 failed로 노출한다. 응답만 성공으로 바꾸지 마라.

[5] GET /state는 줄마다 key=value를 반환한다. ready, finished는 현재 배치의 준비·종료 요청 수다. maximum은 실제 Hikari maximumPoolSize, active·waiting·total은 HikariPoolMXBean의 현재 값이다. peak_active·peak_waiting은 서비스가 5ms마다 표본을 얻어 기록한 실행 전체의 최댓값이다. audits·orders는 DB에서 센 누적 행 수다. GET /records는 감사 행마다 audit|id, 주문 행마다 order|id를 한 줄씩 반환한다. 실험기는 행 수와 각 id의 존재 여부를 모두 확인한다. GET /events는 완료된 요청마다 id|스레드 이름|감사 호출 소요 ms|최하위 예외 클래스를 한 줄로 반환한다. 예외가 없으면 none을 쓴다. 감사 호출 소요 시간은 프록시 진입부터 반환 또는 예외까지이며 순수 풀 획득 시간만을 뜻하지 않는다. DB를 조회하는 /state와 /records는 부하가 끝난 뒤 읽어라.

[6] 먼저 시작 코드로 실험해라. 네 요청이 바깥 커넥션을 잡고 ready=4가 된 뒤 release한다. 각 안쪽 호출이 끝날 때 Batch.finishedAttempt()에서 네 호출이 모두 끝날 때까지 바깥 커넥션을 보유한다. 이 관찰용 유지 장치 때문에 먼저 실패한 요청의 커넥션이 다른 대기자에게 반환되지 않는다. [1]의 풀 크기와 connectionTimeout을 바탕으로 응답별 상태, 두 테이블의 행 수, 감사 호출 대기를 예측한 뒤 관측값과 비교해라. /events에는 Spring의 상위 예외가 아닌 최하위 예외 클래스가 표시된다. OS 스케줄링 때문에 정확히 connectionTimeout에 맞춰 끝난다고 가정하지 마라.

[7] 이 예측을 일반적인 운영 코드와 구별해라. 유지 장치를 제거했을 때 먼저 끝난 요청의 커넥션 반환이 나머지 요청에 어떤 영향을 주는지 추적해라. Hikari 공식 Tn × (Cm − 1) + 1에 값을 넣어 계산하고, 이 값이 처리량의 최적값이 아니라 해당 자원 획득 구조에서 풀 교착을 피하는 충분한 크기임을 설명해라. NESTED는 같은 물리 트랜잭션과 JDBC 세이브포인트를 사용한다. 감사 호출에 NESTED를 적용한 뒤 바깥 트랜잭션을 롤백하면 [4]의 독립 커밋 계약을 지키는지 예측해라.

[8] 부하는 공용 load.main.kts의 개방형 도착 모델로 보낸다. first-wave는 정상 요청 4개 한 배치다. mixed-wave는 배치 두 개를 순차 실행하며 각 배치에서 정상 2개와 rollback=true 2개를 보낸다. 각 배치의 도착 예정 시각은 0·25·50·75ms이며 앞선 응답을 기다려 다음 요청을 보내지 않는다. 모든 요청이 준비되면 release한다. 준비 제한은 15초, 개별 HTTP 요청 제한은 30초다. 모든 요청 종료 뒤 state와 events를 읽는다. 지연 백분위수는 도착 후 게이트 대기를 포함하는 HTTP 왕복 시간이다.

[9] 합격 불변식은 모든 요청이 계약대로 HTTP 200과 정확한 본문을 반환하고, 감사·주문 행의 개수가 [4]와 일치하는 것이다. maximum과 관측된 peak_active·total은 모두 4 이하여야 한다. 종료 뒤 active와 waiting은 0이고 ready와 finished는 4여야 한다. 모든 요청 id의 이벤트가 정확히 한 개씩 있어야 하며 감사 호출 예외는 없어야 한다. p95는 12,000ms 이하여야 한다. 응답 성공·행 수·풀 상한을 함께 판정하므로 타임아웃을 늘리거나 응답을 위조해서 통과할 수 없다.

[10] 실험기는 10개 지표를 출력한다. responses는 올바른 응답 수, audits와 orders는 누적 행 수, maximum·peak_active·peak_waiting은 풀 관측값, remaining은 종료 뒤 active+waiting, events는 고유하고 예외 없는 요청 이벤트 수, p95_ms는 HTTP 지연 p95, violations는 불변식 위반 개수다. 첫 실행에서 responses·events·행 수와 violations를 비교해라. 실험기는 집계 지표만 보여 주고 종료할 때 서버를 정리한다. 요청별 대기와 예외는 [13]~[15]의 별도 관찰 실행으로 확인해라. peak_waiting은 표본 관측값이므로 반드시 특정 값이어야 한다고 판정하지 않는다.

[11] 수정 범위는 src/main/kotlin/lab/Pipeline.kt다. 트랜잭션 경계와 호출 순서를 바꾸되 감사의 독립 커밋, 주문의 원자적 롤백을 유지해라. 풀·게이트·HTTP·계측·스키마·빌드 파일과 .lab-libs는 바꾸지 마라. 풀을 키우거나 풀을 추가하지 마라. Batch.ready()와 Batch.finishedAttempt()는 요청마다 한 번씩 호출해야 하며, 후자는 감사 시도가 끝난 뒤 호출해야 한다. 현재 계약은 프로세스 장애 시 두 작업을 묶는 원자성을 요구하지 않는다.

[12] API 근거는 [Spring 트랜잭션 전파](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html), [Hikari 풀 크기 공식](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing), [HikariPoolMXBean 소스](https://github.com/brettwooldridge/HikariCP/blob/dev/src/main/java/com/zaxxer/hikari/HikariPoolMXBean.java)다. Spring 문서는 REQUIRES_NEW가 바깥 자원을 유지한 채 독립 자원을 얻는다고 설명하며 NESTED의 단일 물리 트랜잭션과 구별한다. MXBean의 활성·대기 수는 순간 표본이므로 이를 실제 성공·행 수와 함께 읽어라.

## 요청별 관찰

[13] Docker Desktop을 켜고 저장소 루트에서 관찰할 프로젝트를 먼저 빌드해라. 아래 셸의 project에는 시작 코드 사본이나 수정 중인 프로젝트 경로를 넣어라. practice의 코드나 진행 기록을 초기화할 필요는 없다.

```sh
project=/tmp/pool-workspace
CRESVOID_ROOT="$PWD" sh "$project/build.sh"
```

[14] 다음 코드를 저장소 루트의 pool-observe.main.kts에 저장하고 kotlin pool-observe.main.kts "$project"로 실행해라. 공용 부하 도구와 고정 실행 이미지로 별도 서버를 띄우고 호스트에서 health 응답이 올 때까지 최대 15초 기다린다. 새 배치를 열고 네 요청을 0·25·50·75ms에 보내며, 15초 안에 gate=4를 확인한 뒤 release한다. 요청이 모두 끝나면 응답·state·records·events를 출력한다. 정상 종료와 예외, Ctrl+C 때 ComposeStack이 컨테이너를 정리한다. 관찰 뒤 임시 스크립트를 지워라.

```kotlin
@file:Import("content/lab-env/load.main.kts")
@file:Import("content/lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

require(args.size == 1)
val project = File(args[0]).canonicalFile
require(File(project, "build/app.jar").isFile)
requireDocker()
fun awaitHost(client: HttpClient, base: String) {
    val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
    while (System.nanoTime() < deadline) {
        val ready = try {
            val reply = client.send(get(base, "/health", Duration.ofSeconds(1)),
                HttpResponse.BodyHandlers.ofString())
            reply.statusCode() == 200 && reply.body() == "ready"
        } catch (_: java.io.IOException) {
            false
        }
        if (ready) return
        Thread.sleep(50)
    }
    error("host endpoint did not become ready")
}

val compose = File.createTempFile("pool-observe-", ".yaml")
compose.writeText("""
services:
  app:
    image: ${'$'}{JVM_RUNNER_IMAGE}
    volumes: ["${'$'}{PROGRAM_DIR}:/work:ro"]
    working_dir: /work
    command: ["lab-run", "spring", "lab.MainKt"]
    environment: { PORT: "8080" }
    ports: ["127.0.0.1::8080"]
    healthcheck:
      test: ["CMD-SHELL", "curl -fs localhost:8080/health >/dev/null"]
      interval: 1s
      timeout: 2s
      retries: 45
    deploy:
      resources:
        limits: { cpus: "1", memory: 512m }
""".trimIndent())

try {
    ComposeStack(compose, "pool-observe", programEnvironment(project)).use { stack ->
        stack.up(60)
        val base = "http://127.0.0.1:${stack.port("app", 8080)}"
        HttpClient.newHttpClient().use { client ->
            fun control(request: HttpRequest): String {
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                check(response.statusCode() == 200)
                return response.body()
            }
            awaitHost(client, base)
            control(post(base, "/batch", "observe"))
            val arrivals = List(4) { index ->
                Arrival(index * 25L, post(base, "/work?id=observe-$index&rollback=false",
                    "", Duration.ofSeconds(30)))
            }
            Executors.newSingleThreadExecutor().use { executor ->
                val pending = executor.submit(Callable { runLoad(client, arrivals) })
                val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
                while (control(get(base, "/gate")) != "4") {
                    check(System.nanoTime() < deadline) { "gate deadline" }
                    Thread.sleep(20)
                }
                println("gate=4")
                control(post(base, "/release", ""))
                pending.get(35, TimeUnit.SECONDS).forEach { outcome ->
                    println("${outcome.arrival.request.uri().query}|${outcome.status}|${outcome.body}|${outcome.error}")
                }
            }
            listOf("/state", "/records", "/events").forEach { path ->
                println("$path\n${control(get(base, path))}")
            }
        }
    }
} finally {
    compose.delete()
}
```

[15] events의 각 행을 [5]의 형식으로 읽고 요청 id별 감사 호출 소요 시간과 최하위 예외를 비교해라. 이 실행은 정상 요청 한 배치의 관찰용이며 합격 판정을 대신하지 않는다. 롤백을 관찰하려면 arrivals에서 index가 홀수인 요청만 rollback=true로 보내고 새 서버에서 다시 실행해라. 서버가 정리됐는지는 docker ps --filter name=cresvoid-pool-observe로 확인해라. 유지 장치가 없는 운영 코드에서는 먼저 실패한 요청이 반환한 바깥 커넥션을 다른 안쪽 호출이 얻을 수 있으므로, 이 단계에서 관찰한 실패 요청 수를 그대로 일반화하지 마라.
