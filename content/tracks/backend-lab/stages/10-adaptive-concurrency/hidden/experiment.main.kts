@file:Import("../../../../../lab-env/load.main.kts")

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.system.exitProcess

data class Profile(val capacity: Int, val serviceMillis: Long, val arrivalsPerSecond: Int)
data class Invocation(val count: AtomicInteger = AtomicInteger(), @Volatile var completed: Boolean = false)

class Dependency(private val profile: Profile) : AutoCloseable {
    val calls = ConcurrentHashMap<String, Invocation>()
    private val inFlight = AtomicInteger()
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val clock = Executors.newSingleThreadScheduledExecutor()
    private val capacity = AtomicInteger(profile.capacity)
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 256)
    val base: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.executor = executor
        server.createContext("/execute") { exchange ->
            exchange.use {
                val requestId = exchange.requestBody.bufferedReader().use { it.readText() }
                val invocation = calls.computeIfAbsent(requestId) { Invocation() }
                invocation.count.incrementAndGet()
                val active = inFlight.incrementAndGet()
                try {
                    val excess = (active - capacity.get()).coerceAtLeast(0).toLong()
                    val contention = min(2_000L, 80L * excess * excess)
                    Thread.sleep(profile.serviceMillis + contention)
                    invocation.completed = true
                    val response = "done:$requestId".toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Connection", "close")
                    exchange.sendResponseHeaders(200, response.size.toLong())
                    exchange.responseBody.use { it.write(response) }
                } finally {
                    inFlight.decrementAndGet()
                }
            }
        }
        server.start()
    }

    fun startChanges() {
        clock.schedule({ capacity.set(profile.capacity / 2) }, 8, TimeUnit.SECONDS)
        clock.schedule({ capacity.set(profile.capacity) }, 16, TimeUnit.SECONDS)
    }

    override fun close() {
        clock.shutdownNow()
        server.stop(0)
        executor.shutdownNow()
        executor.awaitTermination(3, TimeUnit.SECONDS)
    }
}

data class Evaluation(
    val acceptedP99: Double,
    val rejectedP99: Double,
    val goodputRatios: List<Double>,
    val unexpected: Int,
    val contractErrors: Int,
    val attempted: Int,
    val downstreamCalls: Int,
    val rejected: Int,
) {
    val passed: Boolean get() = acceptedP99 <= 650.0 && rejectedP99 <= 250.0 &&
        goodputRatios.all { it >= 0.60 } && unexpected == 0 && contractErrors == 0 && rejected > 0

    fun printMetrics() {
        metric("accepted_p99_ms", acceptedP99)
        metric("rejected_p99_ms", rejectedP99)
        metric("initial_goodput_ratio", goodputRatios[0])
        metric("reduced_goodput_ratio", goodputRatios[1])
        metric("restored_goodput_ratio", goodputRatios[2])
        metric("unexpected_responses", unexpected)
        metric("contract_errors", contractErrors)
        metric("attempted", attempted)
        metric("downstream_calls", downstreamCalls)
        metric("rejected", rejected)
    }
}

fun evaluate(outcomes: List<Outcome>, dependency: Dependency, profile: Profile): Evaluation {
    check(outcomes.size == profile.arrivalsPerSecond * 24) { "incomplete arrival schedule" }
    fun requestId(outcome: Outcome): String =
        outcome.arrival.request.headers().firstValue("X-Request-Id").orElseThrow()
    fun completedOnce(outcome: Outcome): Boolean {
        val id = requestId(outcome)
        val invocation = dependency.calls[id]
        return outcome.body == "done:$id" && invocation?.completed == true && invocation.count.get() == 1
    }
    val stableWindows = listOf(4_000L..7_999L, 12_000L..15_999L, 20_000L..23_999L)
    val accepted = outcomes.filter { it.status == 200 }
    val validAccepted = accepted.filter(::completedOnce)
    val rejected = outcomes.filter { it.status == 429 }
    val capacityRatios = listOf(1.0, 0.5, 1.0)
    val goodputRatios = stableWindows.mapIndexed { index, window ->
        val good = validAccepted.count { it.arrival.atMillis in window && it.elapsedMillis <= 650.0 }
        val ideal = profile.capacity * capacityRatios[index] * 4_000.0 / profile.serviceMillis
        good / ideal
    }
    val stableP99 = stableWindows.maxOf { window ->
        percentile(accepted.filter { it.arrival.atMillis in window }.map { it.elapsedMillis }, 0.99)
    }
    val violations = outcomes.count { outcome ->
        when (outcome.status) {
            200 -> !completedOnce(outcome)
            429 -> outcome.body != "busy" || dependency.calls.containsKey(requestId(outcome))
            else -> false
        }
    } + dependency.calls.keys.count { id ->
        outcomes.none { it.arrival.request.headers().firstValue("X-Request-Id").orElse("") == id }
    }
    return Evaluation(
        stableP99, percentile(rejected.map { it.elapsedMillis }, 0.99), goodputRatios,
        outcomes.count { it.status != 200 && it.status != 429 }, violations, outcomes.size,
        dependency.calls.values.sumOf { it.count.get() }, rejected.size,
    )
}

fun experiment(): Boolean {
    require(args.size == 2) { "program path and scenario are required" }
    val project = File(args[0])
    require(project.isAbsolute && File(project, "run.sh").canExecute())
    val profile = when (args[1]) {
        "weekday-traffic" -> Profile(12, 120, 120)
        "evening-traffic" -> Profile(8, 160, 60)
        else -> error("unknown scenario")
    }
    val port = freePort()
    val base = "http://127.0.0.1:$port"
    Dependency(profile).use { dependency ->
        Program(project, mapOf("PORT" to "$port", "DOWNSTREAM_URL" to dependency.base), base).use {
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2)).build().use { client ->
                    val prefix = UUID.randomUUID().toString()
                    val arrivals = steady(profile.arrivalsPerSecond, 24.0) { index ->
                        val id = "$prefix-$index"
                        post(base, "/work", id, Duration.ofSeconds(5), mapOf("X-Request-Id" to id))
                    }
                    dependency.startChanges()
                    val outcomes = runLoad(client, arrivals)
                    val result = evaluate(outcomes, dependency, profile)
                    result.printMetrics()
                    return result.passed
                }
        }
    }
}

Locale.setDefault(Locale.US)
val result = try {
    if (experiment()) 0 else 1
} catch (error: Exception) {
    System.err.println("experiment error: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(result)
