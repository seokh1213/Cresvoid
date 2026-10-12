interface RefreshServices {
    suspend fun lock(product: String)

    suspend fun fetch(product: String)

    suspend fun unlock(product: String)
}

class FetchFailure(val code: Int) : RuntimeException("가격 조회가 $code 로 실패했다")

data class RefreshPolicy(
    val concurrency: Int,
    val maxAttempts: Int,
    val attemptTimeout: Long,
    val backoffBase: Long,
    val backoffMax: Long,
)

data class RefreshReport(val succeeded: Int, val failed: Int)

class RefreshCommands : CommandGroup {
    override val commands = setOf("ITEM", "LOCK", "RETRY", "BATCH")
    private val attemptsByProduct = LinkedHashMap<String, List<Attempt>>()
    private var lockTimes: LockTimes? = null
    private var retrySettings: RetrySettings? = null

    override fun execute(command: String, args: List<String>): RunOutput? {
        when (command) {
            "ITEM" -> addItem(args)
            "LOCK" -> {
                args.requireSize(2, command)
                lockTimes = LockTimes(lock = parseDuration(args[0]), unlock = parseDuration(args[1]))
            }
            "RETRY" -> {
                args.requireSize(4, command)
                retrySettings = RetrySettings(
                    maxAttempts = parseNumber(args[0], 1L..10L).toInt(),
                    attemptTimeout = parseDuration(args[1]),
                    backoffBase = parseDuration(args[2]),
                    backoffMax = parseDuration(args[3]),
                )
            }
            else -> return runBatch(args)
        }
        return null
    }

    private fun addItem(args: List<String>) {
        if (args.size < 2) throw ScenarioException("ITEM 에는 상품과 시도가 하나 이상 있어야 한다")
        val product = parseName(args[0])
        if (product in attemptsByProduct) throw ScenarioException("이미 있는 상품이다: $product")
        attemptsByProduct[product] = args.drop(1).map(::parseAttempt)
    }

    private fun parseAttempt(token: String): Attempt {
        val (latencyToken, resultToken) = token.split(':').takeIf { it.size == 2 }
            ?: throw ScenarioException("시도는 <지연>:<결과> 꼴이다: $token")
        val latency = parseDuration(latencyToken)
        return if (resultToken == "OK") {
            Attempt.Success(latency)
        } else {
            Attempt.Failure(latency, parseNumber(resultToken, 400L..599L).toInt())
        }
    }

    private fun runBatch(args: List<String>): RunOutput {
        val cancelAt = when (args.size) {
            1 -> null
            3 -> {
                if (args[1] != "CANCEL") throw ScenarioException("BATCH 의 두 번째 인자는 CANCEL 이다")
                parseDuration(args[2])
            }
            else -> throw ScenarioException("BATCH 의 인자 수가 맞지 않다")
        }
        if (attemptsByProduct.isEmpty()) throw ScenarioException("BATCH 앞에 ITEM 이 없다")
        val times = lockTimes ?: throw ScenarioException("LOCK 이 없다")
        val retry = retrySettings ?: throw ScenarioException("RETRY 가 없다")
        val policy = RefreshPolicy(
            concurrency = parseNumber(args[0], 1L..100_000L).toInt(),
            maxAttempts = retry.maxAttempts,
            attemptTimeout = retry.attemptTimeout,
            backoffBase = retry.backoffBase,
            backoffMax = retry.backoffMax,
        )
        val scriptedAttempts = attemptsByProduct.toMap()
        attemptsByProduct.clear()
        return VirtualTime.simulate {
            val log = EventLog(::now)
            val services = FakeRefreshServices(scriptedAttempts, times, log)
            val ending = runToEnd(cancelAt) { refreshPrices(scriptedAttempts.keys.toList(), services, policy) }
            RunOutput(log.lines(), ending.toBatchLine())
        }
    }
}

private data class LockTimes(val lock: Long, val unlock: Long)

private data class RetrySettings(
    val maxAttempts: Int,
    val attemptTimeout: Long,
    val backoffBase: Long,
    val backoffMax: Long,
)

private sealed interface Attempt {
    val latency: Long

    data class Success(override val latency: Long) : Attempt

    data class Failure(override val latency: Long, val code: Int) : Attempt
}

private class FakeRefreshServices(
    private val scriptedAttempts: Map<String, List<Attempt>>,
    private val times: LockTimes,
    private val log: EventLog,
) : RefreshServices {
    private val attemptsMade = mutableMapOf<String, Int>()

    override suspend fun lock(product: String) {
        log.record("start lock $product")
        log.awaitResponse("lock $product", times.lock)
        log.record("ok lock $product")
    }

    override suspend fun fetch(product: String) {
        val attemptNumber = (attemptsMade[product] ?: 0) + 1
        attemptsMade[product] = attemptNumber
        val attempts = scriptedAttempts.getValue(product)
        val attempt = attempts[minOf(attemptNumber, attempts.size) - 1]
        log.record("start fetch $product $attemptNumber")
        log.awaitResponse("fetch $product $attemptNumber", attempt.latency)
        when (attempt) {
            is Attempt.Success -> log.record("ok fetch $product $attemptNumber")
            is Attempt.Failure -> {
                log.record("error fetch $product $attemptNumber ${attempt.code}")
                throw FetchFailure(attempt.code)
            }
        }
    }

    override suspend fun unlock(product: String) {
        log.record("start unlock $product")
        log.awaitResponse("unlock $product", times.unlock)
        log.record("ok unlock $product")
    }
}

private fun Ending<RefreshReport>.toBatchLine(): String = when (this) {
    is Ending.Returned -> "BATCH DONE $time succeeded=${value.succeeded} failed=${value.failed}"
    is Ending.Cancelled -> "BATCH CANCELLED $time"
    is Ending.Stuck -> "BATCH STUCK $time"
}
