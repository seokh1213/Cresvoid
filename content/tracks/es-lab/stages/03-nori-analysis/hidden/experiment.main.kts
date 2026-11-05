@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

data class CheckCase(val path: String, val text: String, val expected: List<String>, val category: String)

fun stringFields(json: String, field: String): List<String> {
    val pattern = Regex("\"$field\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    return pattern.findAll(json).map { match ->
        val encoded = match.groupValues[1]
        buildString {
            var cursor = 0
            while (cursor < encoded.length) {
                val character = encoded[cursor++]
                if (character != '\\') {
                    append(character)
                    continue
                }
                when (val escaped = encoded[cursor++]) {
                    'u' -> {
                        append(encoded.substring(cursor, cursor + 4).toInt(16).toChar())
                        cursor += 4
                    }
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(escaped)
                }
            }
        }
    }.toList()
}

fun analysisCases(): List<CheckCase> = listOf(
    CheckCase("/analyze?profile=none", "초경량패딩", listOf("초경량패딩"), "probe"),
    CheckCase("/analyze?profile=discard", "초경량패딩", listOf("초경량", "패딩"), "probe"),
    CheckCase("/analyze?profile=mixed", "초경량패딩", listOf("초경량패딩", "초경량", "패딩"), "probe"),
    CheckCase("/analyze?profile=mixed", "노바핏", listOf("노바핏"), "probe"),
    CheckCase("/analyze?profile=catalog", "초경량패딩", listOf("초경량패딩", "초경량", "패딩"), "token"),
    CheckCase("/analyze?profile=catalog", "노바핏", listOf("노바핏"), "token"),
    CheckCase("/analyze?profile=catalog", "AB12", listOf("ab", "12"), "token"),
    CheckCase("/analyze?profile=catalog", "AB13", listOf("ab", "13"), "token"),
    CheckCase("/analyze?profile=catalog", "패딩은", listOf("패딩"), "token"),
    CheckCase("/analyze?profile=catalog", "AB99", listOf("ab", "99"), "token"),
)

fun searchCases(ids: List<String>): List<CheckCase> {
    fun query(text: String, vararg rows: Int) = CheckCase("/search", text, rows.map { ids[it - 1] }, "search")
    return listOf(
        query("초경량", 1, 2, 4), query("패딩", 1, 2, 3), query("초경량패딩", 1, 2),
        query("AB12", 1, 3, 4, 5, 6, 8), query("AB13", 2, 7), query("초경량패딩 AB12", 1),
        query("패딩은 AB12", 1, 3), query("노바핏", 5, 7), query("노바핏 AB12", 5),
        query("노바 핏 AB12", 6), query("운동화 AB12", 5, 6), query("미등록 AB99"),
        query("은"), query("AB99"), query("노바핏 AB13", 7), query("ab12", 1, 3, 4, 5, 6, 8),
    )
}

fun measure(client: HttpClient, base: String): Int {
    val prefix = "p" + ProcessHandle.current().pid() + "-"
    val titles = listOf(
        "초경량패딩 AB12", "초경량패딩 AB13", "패딩 AB12", "초경량 가방 AB12",
        "노바핏 운동화 AB12", "노바 핏 운동화 AB12", "노바핏 운동화 AB13", "가방 AB12",
    )
    val ids = titles.indices.map { "$prefix${it + 1}" }
    val products = titles.indices.joinToString("\n") { "${ids[it]}\t${titles[it]}" }
    val catalog = client.send(post(base, "/catalog", products, Duration.ofSeconds(20)), HttpResponse.BodyHandlers.ofString())
    check(catalog.statusCode() == 200 && Regex("\"indexed\"\\s*:\\s*8\\b").containsMatchIn(catalog.body())) {
        "catalog setup failed: ${catalog.statusCode()}"
    }
    val cases = analysisCases() + searchCases(ids)
    val arrivals = steady(10, 10.4) { index ->
        val case = cases[index % cases.size]
        post(base, case.path, case.text, Duration.ofSeconds(15))
    }
    val outcomes = runLoad(client, arrivals)
    var probeMismatches = 0
    var tokenMismatches = 0
    var missingHits = 0
    var extraHits = 0
    var exactSearches = 0
    for ((index, outcome) in outcomes.withIndex()) {
        val case = cases[index % cases.size]
        val success = outcome.status == 200 && outcome.error == null
        val actual = stringFields(outcome.body, if (case.category == "search") "_id" else "token")
        when (case.category) {
            "probe" -> if (!success || actual != case.expected) probeMismatches++
            "token" -> if (!success || actual != case.expected) tokenMismatches++
            "search" -> {
                missingHits += (case.expected.toSet() - actual.toSet()).size
                extraHits += (actual.toSet() - case.expected.toSet()).size + actual.size - actual.distinct().size
                if (success && actual.toSet() == case.expected.toSet() && actual.size == case.expected.size) exactSearches++
            }
        }
        if (!success || (case.category != "search" && actual != case.expected) ||
            (case.category == "search" && actual.toSet() != case.expected.toSet())) {
            System.err.println("${case.category} ${case.text}: status=${outcome.status}, actual=$actual, expected=${case.expected}")
        }
    }
    val completed = outcomes.count { it.status == 200 && it.error == null }
    metric("requests", arrivals.size)
    metric("completed", completed)
    metric("probe_mismatches", probeMismatches)
    metric("token_mismatches", tokenMismatches)
    metric("missing_hits", missingHits)
    metric("extra_hits", extraHits)
    metric("exact_searches", exactSearches)
    metric("search_requests", 64)
    metric("p95_ms", percentile(outcomes.map { it.elapsedMillis }, 0.95))
    return if (completed == 104 && probeMismatches == 0 && tokenMismatches == 0 &&
        missingHits == 0 && extraHits == 0 && exactSearches == 64) 0 else 1
}

fun experiment(): Int {
    require(args.size == 2 && args[1] == "catalog-session")
    val project = File(args[0])
    require(project.isAbsolute && File(project, "run.sh").canExecute())
    ComposeStack(File("compose.yaml"), "es-nori-analysis").use { stack ->
        stack.up(240)
        val port = freePort()
        val base = "http://127.0.0.1:$port"
        val environment = mapOf("PORT" to "$port", "ES_URL" to "http://127.0.0.1:${stack.port("elasticsearch", 9200)}")
        Program(project, environment, base).use {
            HttpClient.newHttpClient().use { client -> return measure(client, base) }
        }
    }
}

val result = try {
    experiment()
} catch (failure: Exception) {
    System.err.println("실험 오류: ${failure.message}")
    EXPERIMENT_ERROR
}
exitProcess(result)
