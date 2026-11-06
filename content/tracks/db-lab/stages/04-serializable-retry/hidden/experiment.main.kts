@file:Import("../../../../../lab-env/docker.main.kts")
@file:Import("../../../../../lab-env/load.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

data class Expected(val status: Int, val attempts: Int, val outcome: String?)
data class CheckResult(val outcome: Outcome, val expected: Expected)

fun fields(body: String): Map<String, String> = body.split(';').filter { '=' in it }
    .associate { it.substringBefore('=') to it.substringAfter('=') }

fun sql(stack: ComposeStack, query: String): String =
    stack.exec("postgres", "psql", "-U", "lab", "-d", "lab", "-At", "-c", query).trim()

fun paired(client: HttpClient, base: String): List<CheckResult> = (1..12).flatMap { round ->
    val requests = (1..2).map { doctor ->
        Arrival(0, post(base, "/leave?round=$round&doctor=$doctor&maxAttempts=3&paired=true", "", Duration.ofSeconds(30)))
    }
    runLoad(client, requests).map { CheckResult(it, Expected(200, 0, null)) }
}

fun bounded(client: HttpClient, base: String): List<CheckResult> {
    val expected = (1..28).map { index ->
        when (index) {
            in 1..8 -> Expected(200, 3, "left")
            in 9..16 -> Expected(409, 3, null)
            in 17..24 -> Expected(422, 1, null)
            else -> Expected(409, 1, null)
        }
    }
    val requests = steady(2, 14.0) { index ->
        val round = index + 1
        val faults = if (round <= 8) 2 else 99
        val state = if (round in 17..24) "23514" else "40001"
        val limit = if (round >= 25) 1 else 3
        post(base, "/leave?round=$round&doctor=1&maxAttempts=$limit&faults=$faults&state=$state", "", Duration.ofSeconds(30))
    }
    return runLoad(client, requests).mapIndexed { index, outcome -> CheckResult(outcome, expected[index]) }
}

fun evaluate(stack: ComposeStack, results: List<CheckResult>, scenario: String): Boolean {
    check(results.none { it.outcome.error != null }) { "HTTP 연결 또는 응답 수신에 실패했다" }
    val paired = scenario == "paired-arrivals"
    val responses = results.map { fields(it.outcome.body) }
    val delays = responses.flatMap { it["delays"].orEmpty().split(',').filter(String::isNotEmpty).mapNotNull(String::toLongOrNull) }
    val responseErrors = results.zip(responses).count { (result, values) ->
        result.outcome.status != result.expected.status || result.outcome.error != null ||
            (result.expected.outcome != null && values["outcome"] != result.expected.outcome) ||
            (result.expected.status != 200 && values["state"] != if (result.expected.status == 409) "40001" else "23514") ||
            values["shortSleeps"] != "0" || values["attempts"]?.toIntOrNull() == null ||
            (result.expected.attempts > 0 && values["attempts"]?.toIntOrNull() != result.expected.attempts)
    }
    val db = sql(stack, """
        SELECT
          (SELECT count(*) FROM (SELECT round_id FROM duty GROUP BY round_id HAVING count(*) FILTER (WHERE active) <> 1) x),
          (SELECT count(*) FROM decisions),
          (SELECT count(*) FROM decisions WHERE outcome='left'),
          (SELECT count(*) FROM decisions WHERE outcome='stayed'),
          (SELECT count(*) FROM attempt_log),
          (SELECT coalesce(max(attempt_no),0) FROM attempt_log)
    """).split('|').map(String::toInt)
    val expectedAttempts = if (paired) 36 else 60
    val expectedDelays = if (paired) 12 else 32
    val traceErrors = responses.count { values ->
        val attempts = values["attempts"]?.toIntOrNull() ?: 0
        val count = values["delays"].orEmpty().split(',').count(String::isNotEmpty)
        attempts !in 1..3 || count != attempts - 1
    }
    val policyErrors = responseErrors + traceErrors +
        (if (db[4] == expectedAttempts && db[5] <= 3) 0 else 1) +
        (if (delays.size == expectedDelays && delays.all { it in 20..80 } && delays.distinct().size >= 2) 0 else 1)
    val databaseErrors = if (paired) {
        db[0] + kotlin.math.abs(db[1] - 24) + kotlin.math.abs(db[2] - 12) + kotlin.math.abs(db[3] - 12) +
            results.zip(responses).chunked(2).count { pair -> pair.map { it.second["outcome"] }.toSet() != setOf("left", "stayed") }
    } else {
        val badRounds = sql(stack, """
            SELECT count(*) FROM (
              SELECT round_id FROM duty GROUP BY round_id
              HAVING count(*) FILTER (WHERE active) <> CASE WHEN round_id<=8 THEN 1 ELSE 2 END
            ) x
        """).toInt()
        badRounds + kotlin.math.abs(db[1] - 8) + kotlin.math.abs(db[2] - 8) + db[3]
    }
    metric("requests", results.size)
    metric("response_errors", responseErrors)
    metric("database_errors", databaseErrors)
    metric("policy_errors", policyErrors)
    metric("transaction_attempts", db[4])
    metric("max_attempts", db[5])
    metric("retry_delays", delays.size)
    metric("distinct_delays", delays.distinct().size)
    metric("p95_ms", percentile(results.map { it.outcome.elapsedMillis }, 0.95))
    return databaseErrors == 0 && policyErrors == 0
}

fun experiment(): Boolean {
    require(args.size == 2 && File(args[0]).isAbsolute)
    val scenario = args[1]
    require(scenario in setOf("paired-arrivals", "bounded-arrivals"))
    requireDocker()
    ComposeStack(File("compose.yaml"), "db-04-serializable", programEnvironment(File(args[0]))).use { stack ->
        stack.up(120)
        val base = "http://127.0.0.1:${stack.port("app", 8080)}"
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().use { client ->
            check(client.send(get(base, "/health"), HttpResponse.BodyHandlers.ofString()).statusCode() == 200)
            val rounds = if (scenario == "paired-arrivals") 12 else 28
            check(client.send(post(base, "/prepare?rounds=$rounds", ""), HttpResponse.BodyHandlers.ofString()).statusCode() == 200)
            val results = if (scenario == "paired-arrivals") paired(client, base) else bounded(client, base)
            return evaluate(stack, results, scenario)
        }
    }
}

val code = try {
    if (experiment()) 0 else 1
} catch (failure: Exception) {
    System.err.println("실험 오류: ${failure.javaClass.simpleName}: ${failure.message}")
    2
}
exitProcess(code)
