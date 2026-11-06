@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlin.system.exitProcess

data class Plan(val arrivals: List<Arrival>, val phaseStart: Long, val minimum: Int)
enum class RequestError(val code: Int) {
    NONE(0), TRANSPORT(1), SERVICE(2), HTTP_STATUS(3), RESPONSE(4), CLOCK(5), HTTP_EXAMPLE(6),
}
data class Judgement(val outcome: Outcome, val user: String, val atMicros: Long?, val error: RequestError) {
    val valid: Boolean get() = error == RequestError.NONE
}
data class Measurement(val judgements: List<Judgement>, val exampleErrors: List<RequestError>, val remainingKeys: Int)

fun redisMicros(stack: ComposeStack): Long {
    val clock = stack.exec("redis", "redis-cli", "--raw", "TIME").trim().lines()
    check(clock.size == 2) { "Redis TIME 응답을 읽을 수 없다" }
    return clock[0].toLong() * 1_000_000 + clock[1].toLong()
}

fun request(base: String, user: String): HttpRequest =
    post(base, "/requests", "", headers = mapOf("X-User" to user))

fun sharedPlan(bases: List<String>): Plan {
    val burst = List(120) { index -> Arrival(0, request(bases[index % 2], "busy")) }
    val continuous = steady(240, 5.0) { index -> request(bases[index % 2], "busy") }
    val quiet = (0..1).flatMap { user ->
        steady(4, 5.0, 100) { index -> request(bases[index % 2], "quiet-$user") }
    }
    return Plan(burst + continuous + quiet, 0, 90)
}

fun pacedPlan(bases: List<String>, stack: ComposeStack): Plan {
    val arrivals = (0..2).flatMap { round ->
        listOf(700L, 1_100L).flatMap { offset ->
            List(80) { index ->
                Arrival(round * 2_000 + offset + index, request(bases[index % 2], "returning"))
            }
        }
    }
    val now = redisMicros(stack)
    val epoch = (now / 1_000_000 + 2) * 1_000_000
    val delay = (epoch - now) / 1_000
    return Plan(arrivals.map { it.copy(atMillis = it.atMillis + delay) }, delay + 700, 10)
}

fun exampleErrors(client: HttpClient, bases: List<String>): List<RequestError> {
    fun verify(request: HttpRequest, expectedStatus: Int, expectedBody: String? = null): RequestError = try {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        when {
            response.statusCode() == 503 -> RequestError.SERVICE
            response.statusCode() != expectedStatus || (expectedBody != null && response.body() != expectedBody) -> RequestError.HTTP_EXAMPLE
            else -> RequestError.NONE
        }
    } catch (_: Exception) {
        RequestError.TRANSPORT
    }
    return listOf(
        verify(request(bases[0], "example"), 200),
        verify(request(bases[1], "example"), 200),
        verify(post(bases[0], "/requests", ""), 400),
        verify(request(bases[1], "INVALID"), 400),
        verify(get(bases[0], "/requests"), 405),
        verify(get(bases[1], "/absent"), 404),
        verify(get(bases[0], "/health"), 200, "ok"),
    ).filter { it != RequestError.NONE }
}

fun judge(outcome: Outcome, bases: List<String>, bounds: LongRange): Judgement {
    val match = Regex("""\{"allowed":(true|false),"decision_us":(\d+),"instance":"([ab])"}""").matchEntire(outcome.body)
    val atMicros = match?.groupValues?.get(2)?.toLongOrNull()
    val expectedInstance = if (outcome.arrival.request.uri().port == URI.create(bases[0]).port) "a" else "b"
    val error = when {
        outcome.error != null -> RequestError.TRANSPORT
        outcome.status == 503 -> RequestError.SERVICE
        outcome.status !in listOf(200, 429) -> RequestError.HTTP_STATUS
        match == null || atMicros == null ||
            (match.groupValues[1] == "true") != (outcome.status == 200) ||
            match.groupValues[3] != expectedInstance -> RequestError.RESPONSE
        atMicros !in bounds -> RequestError.CLOCK
        else -> RequestError.NONE
    }
    return Judgement(outcome, outcome.arrival.request.headers().firstValue("X-User").orElseThrow(), atMicros, error)
}

fun maximumWindow(judgements: List<Judgement>): Int {
    val byUser = judgements.filter { it.valid && it.outcome.status == 200 }.groupBy { it.user }
    return byUser.values.maxOfOrNull { accepted ->
        val times = accepted.map { requireNotNull(it.atMicros) }.sorted()
        var left = 0
        var maximum = 0
        for (right in times.indices) {
            while (times[left] <= times[right] - 1_000_000) left++
            maximum = maxOf(maximum, right - left + 1)
        }
        maximum
    } ?: 0
}

fun minimumAccepted(scenario: String, plan: Plan, judgements: List<Judgement>): Int {
    val accepted = judgements.filter { it.valid && it.outcome.status == 200 }
    if (scenario == "shared-traffic") return accepted.count { it.user == "busy" }
    return (0..2).minOf { round ->
        accepted.count { (it.outcome.arrival.atMillis - plan.phaseStart) / 2_000 == round.toLong() }
    }
}

fun report(scenario: String, plan: Plan, measurement: Measurement): Boolean {
    val judgements = measurement.judgements
    val accepted = judgements.count { it.valid && it.outcome.status == 200 }
    val rejected = judgements.count { it.valid && it.outcome.status == 429 }
    val requestErrors = judgements.count { !it.valid } + measurement.exampleErrors.size
    val firstError = measurement.exampleErrors.firstOrNull()
        ?: judgements.firstOrNull { !it.valid }?.error ?: RequestError.NONE
    val maxWindow = maximumWindow(judgements)
    val minimum = minimumAccepted(scenario, plan, judgements)
    val quietRejections = judgements.count { it.user.startsWith("quiet-") && (!it.valid || it.outcome.status != 200) }
    val p99 = percentile(judgements.map { it.outcome.elapsedMillis }, 0.99)
    metric("requests", plan.arrivals.size)
    metric("accepted", accepted)
    metric("rejected", rejected)
    metric("max_window", maxWindow)
    metric("minimum_accepted", minimum)
    metric("quiet_rejections", quietRejections)
    metric("request_errors", requestErrors)
    metric("first_error", firstError.code)
    metric("p99_ms", p99)
    metric("remaining_keys", measurement.remainingKeys)
    return accepted + rejected == plan.arrivals.size && requestErrors == 0 && rejected > 0 &&
        maxWindow <= 30 && minimum >= plan.minimum && quietRejections == 0 && p99 <= 1_500 && measurement.remainingKeys == 0
}

fun measure(scenario: String, bases: List<String>, stack: ComposeStack, prefix: String): Boolean {
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
        val extraErrors = exampleErrors(client, bases)
        val before = redisMicros(stack)
        val plan = if (scenario == "shared-traffic") sharedPlan(bases) else pacedPlan(bases, stack)
        val outcomes = runLoad(client, plan.arrivals)
        val after = redisMicros(stack)
        val judgements = outcomes.map { judge(it, bases, before..after) }
        Thread.sleep(2_700)
        val keys = stack.exec("redis", "redis-cli", "--scan", "--pattern", "$prefix:*")
            .lineSequence().count { it.isNotBlank() }
        return report(scenario, plan, Measurement(judgements, extraErrors, keys))
    }
}

fun experiment(): Boolean {
    require(args.size == 2) { "프로그램 절대 경로와 시나리오 이름을 지정해라" }
    val program = File(args[0])
    require(program.isAbsolute && File(program, "run.sh").canExecute())
    val scenario = args[1]
    require(scenario in listOf("shared-traffic", "paced-traffic"))
    requireDocker()
    ComposeStack(File("compose.yaml"), "backend-09-rate-limit").use { stack ->
        stack.up()
        val prefix = "rate-${UUID.randomUUID()}"
        val environment = mapOf("REDIS_HOST" to "127.0.0.1", "REDIS_PORT" to stack.port("redis", 6379).toString(), "REDIS_PREFIX" to prefix)
        val firstPort = freePort()
        val firstBase = "http://127.0.0.1:$firstPort"
        Program(program, environment + mapOf("PORT" to firstPort.toString(), "INSTANCE_ID" to "a"), firstBase).use {
            val secondPort = freePort()
            val secondBase = "http://127.0.0.1:$secondPort"
            Program(program, environment + mapOf("PORT" to secondPort.toString(), "INSTANCE_ID" to "b"), secondBase).use {
                return measure(scenario, listOf(firstBase, secondBase), stack, prefix)
            }
        }
    }
}

val code = try {
    if (experiment()) 0 else 1
} catch (error: Exception) {
    System.err.println("실험 오류: ${error.message}")
    2
}
exitProcess(code)
