@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.time.Duration
import kotlin.system.exitProcess

fun rows(vararg values: String): String = "ROWS:" + values.sorted().joinToString(";")

val sampleGroups = rows("1,2,2", "2,1,1", "~,2,0", "-1,1,1", "3,1,1")
val alternateGroups = rows("2,2,2", "~,1,0", "-1,1,1", "4,1,1")
val sampleKeys = rows("1", "2", "~", "-1", "3")
val alternateKeys = rows("2", "~", "-1", "4")
val sampleJoin = rows("a,u", "a,v", "b,u", "b,v", "d,w", "d,x", "e,w", "e,x", "f,y")
val alternateJoin = rows("a,u", "a,v", "d,u", "d,v", "b,w", "c,x", "c,y")
val sampleUnion = rows("ann,east", "ann,east", "bob,west", "cy,south", "ann,east")
val alternateUnion = rows("dee,north", "dee,north", "eli,west")

val sampleRepairedJoin = rows(
    "a,u,2", "a,v,2", "b,u,2", "b,v,2", "c,~,0", "d,w,2", "d,x,2", "e,w,2", "e,x,2", "f,y,1", "g,~,0",
)
val alternateRepairedJoin = rows("a,u,2", "a,v,2", "b,w,1", "c,x,2", "c,y,2", "d,u,2", "d,v,2", "e,~,0")

val observationsExpected = mapOf(
    "sample" to mapOf(
        "eq_join" to rows("a,u", "a,v", "b,u", "b,v", "f,y"),
        "safe_join" to sampleJoin,
        "not_in" to rows(),
        "groups" to sampleGroups,
        "distinct_keys" to sampleKeys,
        "counts" to rows("7,5"),
        "positional_union" to rows("ann,east", "ann,east", "bob,west", "south,cy", "east,ann"),
        "named_union" to sampleUnion,
        "strict_cast" to "ERROR:CAST_INVALID_INPUT",
        "strict_add" to "ERROR:ARITHMETIC_OVERFLOW",
    ),
    "alternate" to mapOf(
        "eq_join" to rows("a,u", "a,v", "d,u", "d,v", "c,x", "c,y"),
        "safe_join" to alternateJoin,
        "not_in" to rows("a", "d", "e"),
        "groups" to alternateGroups,
        "distinct_keys" to alternateKeys,
        "counts" to rows("5,4"),
        "positional_union" to rows("dee,north", "north,dee", "west,eli"),
        "named_union" to alternateUnion,
        "strict_cast" to "ERROR:CAST_INVALID_INPUT",
        "strict_add" to "ERROR:ARITHMETIC_OVERFLOW",
    ),
)

val repairsExpected = mapOf(
    "sample" to mapOf(
        "join" to sampleRepairedJoin,
        "exclude" to rows("a", "b", "f", "g"),
        "groups" to rows("1,2,2,2", "2,1,1,0", "~,2,0,2", "-1,1,1,1", "3,1,1,0"),
        "distinct_keys" to rows("1,2", "2,1", "~,2", "-1,1", "3,1"),
        "counts" to rows("7,5,5,3,5"),
        "union" to rows("ann,east,2,1", "bob,west,1,0", "cy,south,0,1"),
        "cast" to rows("t1,7", "t2,~", "t3,~", "t4,~", "t5,-2"),
        "add" to rows("t1,2", "t2,~", "t3,~", "t4,-1", "t5,5"),
    ),
    "alternate" to mapOf(
        "join" to alternateRepairedJoin,
        "exclude" to rows("a", "b", "d", "e"),
        "groups" to rows("2,2,2,2", "~,1,0,1", "-1,1,1,2", "4,1,1,0"),
        "distinct_keys" to rows("2,2", "~,1", "-1,1", "4,1"),
        "counts" to rows("5,4,5,4,4"),
        "union" to rows("dee,north,1,1", "eli,west,0,1"),
        "cast" to rows("t1,-8", "t2,~", "t3,~", "t4,0"),
        "add" to rows("t1,-2147483647", "t2,1", "t3,~", "t4,~"),
    ),
)

data class Probe(val endpoint: String, val case: String)

val probes = listOf(
    Probe("observe", "sample"), Probe("repair", "sample"),
    Probe("observe", "alternate"), Probe("repair", "alternate"),
)

fun parseBundle(body: String, keys: Set<String>): Map<String, String>? {
    val lines = body.trimEnd('\n').split('\n')
    val entries = lines.map { line ->
        val parts = line.split('\t')
        if (parts.size != 2 || !(parts[1].startsWith("ROWS:") || parts[1].startsWith("ERROR:"))) return null
        parts[0] to parts[1]
    }
    val results = entries.toMap()
    return results.takeIf { entries.size == keys.size && results.keys == keys }
}

fun measure(base: String): Int {
    val mismatch = linkedMapOf(
        "observation_mismatches" to 0, "join_mismatches" to 0,
        "exclusion_mismatches" to 0, "aggregate_mismatches" to 0,
        "union_mismatches" to 0, "ansi_mismatches" to 0,
    )
    val family = mapOf(
        "join" to "join_mismatches", "exclude" to "exclusion_mismatches",
        "groups" to "aggregate_mismatches", "distinct_keys" to "aggregate_mismatches",
        "counts" to "aggregate_mismatches", "union" to "union_mismatches",
        "cast" to "ansi_mismatches", "add" to "ansi_mismatches",
    )
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    val arrivals = steady(1, 4.0) { index ->
        val probe = probes[index]
        get(base, "/${probe.endpoint}?case=${probe.case}", Duration.ofSeconds(120))
    }
    val outcomes = runLoad(client, arrivals)
    check(outcomes.none { it.error != null }) { "HTTP transport failed" }
    var responseErrors = 0
    outcomes.zip(probes).forEach { (outcome, probe) ->
        val expected = if (probe.endpoint == "observe") observationsExpected.getValue(probe.case)
            else repairsExpected.getValue(probe.case)
        val actual = parseBundle(outcome.body, expected.keys)
        if (outcome.status != 200 || actual == null) {
            responseErrors++
        } else {
            expected.forEach { (name, value) ->
                if (actual[name] != value) {
                    val counter = if (probe.endpoint == "observe") "observation_mismatches" else family.getValue(name)
                    mismatch[counter] = mismatch.getValue(counter) + 1
                }
            }
        }
    }
    metric("scheduled", arrivals.size)
    metric("completed", outcomes.size)
    metric("response_errors", responseErrors)
    mismatch.forEach { (name, value) -> metric(name, value) }
    metric("p95_ms", percentile(outcomes.map { it.elapsedMillis }, 0.95))
    return if (outcomes.size == 4 && responseErrors == 0 && mismatch.values.all { it == 0 }) 0 else 1
}

fun runExperiment(): Int {
    require(args.size == 2) { "Expected program directory and scenario name" }
    val program = File(args[0])
    require(program.isAbsolute && File(program, "build/app.jar").isFile) { "Expected built absolute program directory" }
    require(args[1] == "query-workload") { "Unknown scenario" }
    requireDocker()
    val stack = ComposeStack(File("compose.yaml"), "spark-null-semantics", programEnvironment(program))
    try {
        stack.up(120)
        val base = "http://127.0.0.1:${stack.port("app", 8080)}"
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        val health = client.send(get(base, "/health"), java.net.http.HttpResponse.BodyHandlers.ofString())
        check(health.statusCode() == 200 && health.body() == "ready\n") { "Invalid health response" }
        return measure(base)
    } finally {
        stack.close()
    }
}

val result = try {
    runExperiment()
} catch (error: Exception) {
    System.err.println("Experiment error: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(result)
