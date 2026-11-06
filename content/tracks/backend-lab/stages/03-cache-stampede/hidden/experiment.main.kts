@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")
@file:Import("../../../../../lab-env/kotlin/RedisClient.kt")
@file:Import("OwnershipProxy.kt")

import java.io.File
import java.net.http.HttpClient
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

data class ProbeResults(val outcomes: List<Outcome>, val ownership: Int, val recovery: Int, val distinctKeys: Int)
data class OwnershipResult(val outcomes: List<Outcome>, val violations: Int)

fun probeAtomicRelease(program: File, redisPort: Int, originDelay: Long): OwnershipResult {
    val successor = UUID.randomUUID().toString()
    OwnershipProxy(redisPort, successor).use { proxy ->
        val base = "http://127.0.0.1:${freePort()}"
        val environment = mapOf(
            "REDIS_HOST" to "127.0.0.1", "REDIS_PORT" to proxy.port.toString(),
            "ORIGIN_DELAY_MS" to originDelay.toString(), "PORT" to base.substringAfterLast(':'),
        )
        Program(program, environment, base).use {
            RedisClient("127.0.0.1", redisPort).use { redis ->
                HttpClient.newHttpClient().use { client ->
                    return exerciseAtomicRelease(client, redis, base, proxy)
                }
            }
        }
    }
}

fun exerciseAtomicRelease(client: HttpClient, redis: RedisClient, base: String, proxy: OwnershipProxy): OwnershipResult {
    redis.set("origin:gate:93", "closed", expireMillis = 10_000)
    Executors.newSingleThreadExecutor().use { worker ->
        val pending = worker.submit<List<Outcome>> {
            runLoad(client, listOf(Arrival(0, get(base, "/products/93"))))
        }
        try {
            awaitOrigin(redis, 93)
            proxy.arm()
        } finally {
            redis.delete("origin:gate:93")
        }
        val outcomes = pending.get(10, TimeUnit.SECONDS)
        val violated = proxy.replaced.get() && !proxy.successorSurvived(redis)
        return OwnershipResult(outcomes, if (violated) 1 else 0)
    }
}

fun originCalls(redis: RedisClient, id: Int): Long = redis.get("origin:calls:$id")?.toLong() ?: 0L

fun awaitOrigin(redis: RedisClient, id: Int) {
    val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
    while (originCalls(redis, id) == 0L) {
        check(System.nanoTime() < deadline) { "원천 조회 시작을 확인하지 못했다" }
        Thread.sleep(5)
    }
}

fun probeOwnership(client: HttpClient, redis: RedisClient, base: String): Pair<List<Outcome>, Int> {
    redis.set("origin:gate:99", "closed", expireMillis = 10_000)
    val token = UUID.randomUUID().toString()
    Executors.newSingleThreadExecutor().use { worker ->
        val pending = worker.submit<List<Outcome>> { runLoad(client, listOf(Arrival(0, get(base, "/products/99")))) }
        try {
            awaitOrigin(redis, 99)
            redis.set("lock:product:99", token, expireMillis = 5_000)
        } finally {
            redis.delete("origin:gate:99")
        }
        val responses = pending.get(10, TimeUnit.SECONDS)
        return responses to if (redis.get("lock:product:99") == token) 0 else 1
    }
}

fun probes(client: HttpClient, redis: RedisClient, bases: List<String>): ProbeResults {
    val ownership = probeOwnership(client, redis, bases[0])
    redis.set("lock:product:98", "departed-owner", expireMillis = 300)
    val recovery = runLoad(client, steady(500, 0.04) { index -> get(bases[index % 2], "/products/98") })
    val independent = runLoad(client, (94..97).flatMap { id ->
        bases.map { base -> Arrival(0, get(base, "/products/$id")) }
    })
    return ProbeResults(
        ownership.first + recovery + independent,
        ownership.second,
        if (originCalls(redis, 98) == 1L) 0 else 1,
        (94..97).count { originCalls(redis, it) != 1L },
    )
}

class CacheWatch(private val redis: RedisClient) : AutoCloseable {
    val expirations = AtomicInteger()
    val invalidTtl = AtomicBoolean()
    private val running = AtomicBoolean(true)
    private val worker = Executors.newSingleThreadExecutor()
    private val pending = worker.submit {
        var present = false
        while (running.get()) {
            val ttl = redis.ttlMillis("product:1")
            if (ttl == -1L || ttl > 5_000L) invalidTtl.set(true)
            val exists = ttl != -2L
            if (present && !exists) expirations.incrementAndGet()
            present = exists
            Thread.sleep(25)
        }
    }

    override fun close() {
        running.set(false)
        try {
            pending.get(5, TimeUnit.SECONDS)
        } finally {
            worker.shutdownNow()
        }
    }
}

val jsonBodyTokens = Regex("""("(?:\\.|[^"\\])*")|[ \t\r\n]+""")

fun wrongBodies(outcomes: List<Outcome>): Int = outcomes.count { outcome ->
    val id = outcome.arrival.request.uri().path.substringAfterLast('/').toInt()
    val expected = """{"id":$id,"name":"product-$id","price":${id * 100}}"""
    val compact = jsonBodyTokens.replace(outcome.body) { match -> match.groups[1]?.value ?: "" }
    outcome.status == 200 && compact != expected
}

fun measure(client: HttpClient, redis: RedisClient, bases: List<String>, atomic: OwnershipResult): Int {
    val checks = probes(client, redis, bases)
    val arrivals = steady(500, 40.0) { index ->
        val id = if (index % 5 < 4) 1 else 2 + (index / 5) % 4
        get(bases[index % 2], "/products/$id")
    }
    val watch = CacheWatch(redis)
    val outcomes = watch.use { runLoad(client, arrivals) }
    val allOutcomes = atomic.outcomes + checks.outcomes + outcomes
    val calls = originCalls(redis, 1)
    val expirations = watch.expirations.get()
    val budget = expirations + 2
    val errors = allOutcomes.count { it.status != 200 || it.error != null }
    val slow = allOutcomes.count { it.elapsedMillis > 2_000.0 }
    val bodyViolations = wrongBodies(allOutcomes)
    val ownershipViolations = checks.ownership + atomic.violations
    val ttlViolations = if (watch.invalidTtl.get()) 1 else 0
    metric("hot_origin_calls", calls)
    metric("hot_expirations", expirations)
    metric("http_errors", errors)
    metric("over_two_seconds", slow)
    metric("ownership_violations", ownershipViolations)
    metric("recovery_violations", checks.recovery)
    metric("body_violations", bodyViolations)
    metric("distinct_key_violations", checks.distinctKeys)
    metric("ttl_violations", ttlViolations)
    metric("completed_requests", outcomes.size)
    return if (calls <= budget && errors == 0 && slow == 0 && ownershipViolations == 0 &&
        checks.recovery == 0 && bodyViolations == 0 && checks.distinctKeys == 0 && ttlViolations == 0 &&
        expirations >= 5 && outcomes.size == 20_000) 0 else 1
}

fun experiment(program: File, originDelay: Long): Int {
    ComposeStack(File("compose.yaml"), "backend-cache-stampede").use { stack ->
        stack.up()
        val redisPort = stack.port("redis", 6379)
        val atomic = probeAtomicRelease(program, redisPort, originDelay)
        val firstPort = freePort()
        var secondPort = freePort()
        while (secondPort == firstPort) secondPort = freePort()
        val bases = listOf("http://127.0.0.1:$firstPort", "http://127.0.0.1:$secondPort")
        val environment = mapOf(
            "REDIS_HOST" to "127.0.0.1", "REDIS_PORT" to redisPort.toString(),
            "ORIGIN_DELAY_MS" to originDelay.toString(),
        )
        Program(program, environment + ("PORT" to firstPort.toString()), bases[0]).use {
            Program(program, environment + ("PORT" to secondPort.toString()), bases[1]).use {
                return measureServices(redisPort, bases, atomic)
            }
        }
    }
}

fun measureServices(redisPort: Int, bases: List<String>, atomic: OwnershipResult): Int {
    RedisClient("127.0.0.1", redisPort).use { redis ->
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2)).build().use { client ->
                return measure(client, redis, bases, atomic)
            }
    }
}

val result = try {
    require(args.size == 2) { "프로그램 절대 경로와 시나리오 이름이 필요하다" }
    val program = File(args[0])
    require(program.isAbsolute && File(program, "run.sh").canExecute()) { "프로그램 경로가 올바르지 않다" }
    val delay = when (args[1]) {
        "storefront-morning" -> 200L
        "storefront-evening" -> 1_000L
        else -> error("알 수 없는 시나리오")
    }
    experiment(program, delay)
} catch (error: Exception) {
    System.err.println("실험 오류: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(result)
