# 재고 차감과 동시 주문

[1] 한정 수량 상품의 주문 서비스를 완성하라. 먼저 시작 코드를 읽고 가능한 요청 실행 순서와 성공 주문 수·실제 차감량의 관계를 예측하라. 예측을 적고 문항을 푼 뒤 시작 코드를 빌드하고 실험하라. 예측과 관찰을 비교하여 원인을 설명하고, 코드를 고친 뒤 같은 실험으로 다시 측정하라.

[2] 서비스는 JDK 21의 HTTP 서버와 메모리 저장소로 실행된다. PORT 환경 변수가 정한 127.0.0.1 포트에 바인딩하며 GET /health는 준비가 끝나면 200과 {"status":"ready"}를 반환한다. 저장소 하나를 OrderService 인스턴스 두 개가 공유하고 주문을 번갈아 처리한다. warmup, sku-1, contract 상품의 초기 재고는 각각 100개다. 프로세스를 새로 띄우면 재고도 초기화된다. 외부 서비스나 추가 라이브러리는 필요 없다.

[3] POST /orders는 JSON 객체 {"productId":"sku-1","quantity":1}을 받는다. 두 필드의 순서는 바뀔 수 있다. productId는 영문자·숫자·하이픈으로 이루어진 문자열이며 quantity는 1 이상 10000 이하의 정수다. 다른 필드나 누락된 필드, 0 이하 수량, 잘못된 JSON, 4096바이트를 넘는 본문은 400과 {"error":"invalid-order"}를 반환한다. 존재하지 않는 상품은 404와 {"error":"not-found"}를 반환한다. 올바른 주문은 수량만큼 재고를 줄이고 201과 {"status":"accepted"}를 반환한다. 재고가 수량보다 작으면 재고를 바꾸지 않고 409와 {"error":"insufficient-stock"}를 반환한다. 응답의 JSON 필드와 값은 이 계약을 따르며 공백은 자유다.

[4] GET /stock/{productId}는 200과 {"productId":"sku-1","remaining":100}처럼 현재 재고를 반환한다. 없는 상품은 404와 {"error":"not-found"}다. 이 조회는 관찰용이며 재고를 고치지 않는다. OrderService.place(productId, quantity)는 존재하는 상품과 유효한 수량만 받는다. 차감했으면 true, 재고 부족이면 false를 반환한다. 시작 코드는 읽은 값과 쓸 값 사이에 Thread.sleep(10)으로 10ms 지연을 둔다. 이 지연을 유지하여 요청이 겹치는 구간을 관찰하라.

[5] order-window 실험은 서버 준비를 기다린 뒤 contract 상품에 순차 주문을 보낸다. 수량 101은 409이며 재고 100을 유지하고, 수량 3은 201이며 재고 97, 이어 수량 97은 201이며 재고 0이 된다. 그 뒤 수량 1은 409다. 수량 0과 -1 및 빈 객체는 400, missing 상품은 404여야 한다. 거절된 주문은 재고를 바꾸지 않아야 한다. 이 검사는 모두 별도 상품으로 실행하므로 sku-1의 측정 전 재고는 100이다.

[6] 부하는 공용 Kotlin 도구의 개방형 constant-arrival-rate로 만든다. 앞 요청이 끝나지 않아도 예정 시각에 다음 요청을 보낸다. warmup 상품에 초당 400개씩 5초간 총 2000개를 보내 예열하고, 이어 sku-1에 수량 1 주문을 초당 400개씩 20초간 총 8000개 보낸다. 예열은 별도 상품으로 수행하며 본 측정의 성공 수와 거절 수에서는 제외한다. 요청은 재시도하지 않는다. 각 요청의 제한 시간은 30초이며 모든 요청이 끝난 뒤 최종 재고를 조회한다. 실험 한 번의 제한 시간은 180초다.

[6.1] 아래 명령은 저장소 루트에서 실행하라. start가 만든 작업 공간의 코드를 읽고 예측을 적은 뒤 quiz를 풀어라. 문항을 모두 닫으면 test로 order-window 실험을 실행할 수 있다. test는 빌드, 서버 시작, 순차 검사, 예열, 본 측정, 지표 출력, 서버 종료를 처리한다. 시작 코드는 불변식 실패가 예상되며 실패 화면에도 지표를 표시한다. 수정 후 같은 test 명령으로 다시 측정하라. 구체적인 실행 순서와 수정 해설은 관찰·수정 후 단계의 hidden/solution-notes.md에서 확인하라.

```sh
engine/bin/cresvoid start backend-lab -stage 1
engine/bin/cresvoid quiz backend-lab -stage 1
engine/bin/cresvoid test backend-lab -stage 1
```

[6.2] 부하 도구의 저장소 상대 경로는 content/lab-env/load.main.kts다. 기본 실험에는 직접 부하를 작성할 필요가 없다. 별도 관찰이 필요하면 저장소 루트에 아래 내용을 observe.main.kts로 저장하고 kotlin observe.main.kts <작업 공간 절대 경로>로 실행하라. 이 예는 준비 상태 조회 한 번만 확인하고 서버를 종료한다. 본 측정의 부하와 지표는 [6]~[8] 및 test 명령을 기준으로 삼아라. Import 경로는 스크립트 파일의 위치를 기준으로 해석된다.

```kotlin
@file:Import("content/lab-env/load.main.kts")

import java.io.File
import java.net.http.HttpClient

val workspace = File(args.single())
require(workspace.isAbsolute)
val build = ProcessBuilder("./build.sh").directory(workspace)
    .apply { environment().remove("KOTLIN_RUNNER") }.inheritIO().start()
check(build.waitFor() == 0)
val port = freePort()
val base = "http://127.0.0.1:$port"
Program(workspace, mapOf("PORT" to port.toString()), base).use {
    HttpClient.newHttpClient().use { client ->
        val result = runLoad(client, listOf(Arrival(0, get(base, "/health")))).single()
        check(result.status == 200)
        metric("health_status", result.status)
    }
}
```

[7] accepted_orders는 본 측정의 201 응답 수, rejected_orders는 409 응답 수, remaining_stock은 최종 재고다. conservation_gap은 accepted_orders와 (100 − remaining_stock)의 차이의 절댓값이다. scheduled_orders는 본 측정의 예정 요청 수 8000, completed_orders는 HTTP 응답을 받은 본 측정 요청 수다. server_errors는 순차 검사·예열·본 측정·최종 조회의 5xx 수, transport_errors는 같은 범위의 연결 실패와 타임아웃 수다. unexpected_responses는 예열·본 측정에서 201·409가 아니거나 해당 응답 본문이 계약과 다른 수다. contract_errors는 순차 검사와 최종 조회에서 상태 코드나 본문이 계약과 다른 수다. 최종 재고를 읽지 못하면 remaining_stock을 -1로 표시하고 contract_errors도 늘린다.

[8] 201 응답 수는 100에서 남은 재고를 뺀 값과 같아야 한다. 남은 재고는 0 이상이어야 하며 5xx는 0건이어야 한다. 충분한 수량 1 주문을 보냈으므로 accepted_orders는 100, remaining_stock은 0, rejected_orders는 7900이어야 한다. scheduled_orders와 completed_orders는 모두 8000이어야 하고 conservation_gap, server_errors, transport_errors, unexpected_responses, contract_errors는 모두 0이어야 한다. 성공 주문을 전부 거절하거나 재고를 남기는 구현도 실패한다. 지연 시간의 백분위수로 합격을 판정하지 않는다.

[9] 수정 범위는 starter/src/main/kotlin/OrderService.kt의 place 본문과 그 본문에 필요한 잠금 상태다. 요청 처리기, 저장소, 상품 초기값, 응답, 10ms 지연, build.sh와 run.sh는 바꾸지 마라. 새 라이브러리를 추가하지 마라. 읽기·수량 확인·차감이 한 상품에 대해 나눌 수 없는 갱신이 되도록 고쳐라. 같은 JVM의 두 서비스가 같은 재고를 고칠 수 있다는 점을 고려하라. 고친 뒤 예열과 본 측정을 함께 반복하여 성공 수와 차감량이 일치하는지 확인하라.

[10] 실행 순서를 예측할 때는 각 요청의 읽기, 수량 확인, 지연, 쓰기, 반환을 구분하라. 한 요청 안에서의 순서와 서로 다른 요청 사이에서 가능한 순서를 따로 검토하라. 재고 조회 결과와 성공 응답 수를 각각 추적하여 [7]의 지표를 계산하라.

[11] 수정안을 검토할 때는 두 서비스가 공유하는 상태와 각 서비스에만 속한 상태를 구분하라. 어떤 요청들이 서로 기다리는지, 수량을 확인한 뒤 차감할 때까지 다른 요청이 무엇을 바꿀 수 있는지 설명하라. 실험 대상은 JVM 하나이며 여러 서버 프로세스 사이의 정합성은 검증하지 않는다.

[12] 이 단계는 한 상품의 정합성을 실험하며 다른 키의 처리량은 합격 기준으로 삼지 않는다. 실험에서 확인한 성질과 측정하지 않은 성질을 구분하여 결론을 적어라.

[13] 실험 기록에는 수정 전 예측, 관찰한 지표, 원인, 수정 이유, 재측정 결과를 남겨라. 한 번 통과한 결과만으로 가능한 모든 실행 순서가 안전하다고 단정하지 말고, 수정한 코드가 [3]~[4]의 계약을 지키는 이유도 설명하라.

[14] API 근거는 JDK 21의 AtomicInteger 문서(get, set, compareAndSet), JLS 21 §17.1의 모니터 규칙, ConcurrentHashMap 문서(compute), PostgreSQL의 UPDATE 문서(WHERE 조건과 명령 태그 UPDATE count)다. 각각 https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html , https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.1 , https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#compute(K,java.util.function.BiFunction) , https://www.postgresql.org/docs/current/sql-update.html 을 참고하라.
