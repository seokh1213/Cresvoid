import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class LookupCommands : CommandGroup {
    override val commands = setOf("SERVICE", "SEED", "ASK", "LOOKUPS")
    private val plans = LinkedHashMap<String, List<FetchPlan>>()
    private val seeds = LinkedHashMap<String, SeedPrice>()
    private val requests = ArrayList<LookupRequest>()
    private val requestIds = HashSet<String>()

    override fun execute(command: String, args: List<String>): RunOutput? = when (command) {
        "SERVICE" -> { addService(args); null }
        "SEED" -> { addSeed(args); null }
        "ASK" -> { addRequest(args); null }
        else -> runLookups(args)
    }

    private fun addService(args: List<String>) {
        if (args.size < 2) throw ScenarioException("SERVICE 에는 상품과 응답이 하나 이상 있어야 한다")
        val product = parseName(args[0])
        if (product in plans) throw ScenarioException("이미 있는 SERVICE 다: $product")
        plans[product] = args.drop(1).map(::parseFetchPlan)
    }

    private fun addSeed(args: List<String>) {
        args.requireSize(3, "SEED")
        val product = parseName(args[0])
        if (product in seeds) throw ScenarioException("이미 있는 SEED 다: $product")
        seeds[product] = SeedPrice(
            product,
            parseNumber(args[1]).toInt(),
            parseNumber(args[2]),
        )
    }

    private fun addRequest(args: List<String>) {
        args.requireSize(5, "ASK")
        val at = parseDuration(args[0])
        if (requests.isNotEmpty() && at < requests.last().at) {
            throw ScenarioException("ASK 시각은 앞 ASK 보다 이르지 않아야 한다")
        }
        val id = parseName(args[1])
        if (!requestIds.add(id)) throw ScenarioException("이미 있는 ASK id 다: $id")
        val timeout = parseNumber(args[3])
        val cancelAt = parseNumber(args[4])
        if (cancelAt != 0L && cancelAt <= at) throw ScenarioException("취소 시각은 ASK 뒤여야 한다")
        if (timeout != 0L && at + timeout == cancelAt) {
            throw ScenarioException("같은 요청의 취소와 시간 제한은 겹칠 수 없다")
        }
        requests += LookupRequest(at, id, parseName(args[2]), timeout, cancelAt)
    }

    private fun runLookups(args: List<String>): RunOutput {
        args.requireSize(1, "LOOKUPS")
        if (requests.isEmpty()) throw ScenarioException("LOOKUPS 앞에 ASK 가 없다")
        if (requests.any { it.product !in plans }) throw ScenarioException("ASK 상품의 SERVICE 가 없다")
        if (seeds.keys.any { it !in plans }) throw ScenarioException("SEED 상품의 SERVICE 가 없다")
        val firstRequestAt = HashMap<String, Long>()
        requests.forEach { firstRequestAt.putIfAbsent(it.product, it.at) }
        if (seeds.values.any { seed -> firstRequestAt[seed.product]?.let { it < seed.writtenAt } == true }) {
            throw ScenarioException("SEED 기록 시각은 첫 ASK 보다 늦을 수 없다")
        }
        val ttl = parseDuration(args[0])
        val planned = plans.toMap()
        val cached = seeds.values.toList()
        val asked = requests.toList()
        plans.clear()
        seeds.clear()
        requests.clear()
        requestIds.clear()
        return VirtualTime.simulate { runLookupScenario(ttl, planned, cached, asked) }
    }
}

private data class FetchPlan(val latency: Long, val value: Int?, val failure: Int?)

private fun parseFetchPlan(token: String): FetchPlan {
    val parts = token.split(':')
    if (parts.size != 3) throw ScenarioException("응답은 <지연>:OK:<값> 또는 <지연>:FAIL:<코드>다")
    val latency = parseDuration(parts[0])
    return when (parts[1]) {
        "OK" -> FetchPlan(latency, parseNumber(parts[2]).toInt(), null)
        "FAIL" -> FetchPlan(latency, null, parseNumber(parts[2], 400L..599L).toInt())
        else -> throw ScenarioException("응답 종류는 OK 나 FAIL 이다")
    }
}

private data class LookupRequest(
    val at: Long,
    val id: String,
    val product: String,
    val timeout: Long,
    val cancelAt: Long,
)

private data class LookupReply(val id: String, val status: String, val time: Long, val detail: String = "") {
    fun line(): String = "LOOKUP $id $status $time" + if (detail.isEmpty()) "" else " $detail"
}

private enum class StopReason { CANCELLED, TIMEOUT }

private class ScheduledRequest(val job: Job, val timers: MutableList<Job>, var stop: StopReason? = null)

private fun VirtualTime.runLookupScenario(
    ttl: Long,
    plans: Map<String, List<FetchPlan>>,
    seeds: List<SeedPrice>,
    requests: List<LookupRequest>,
): RunOutput {
    val log = EventLog(::now)
    val services = FakeLookupServices(plans, log, ::now)
    val owner = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
    val lookup = SingleFlightLookup(owner, ttl, services, seeds)
    val replies = arrayOfNulls<LookupReply>(requests.size)
    val scheduled = requests.mapIndexed { index, request ->
        val timers = mutableListOf<Job>()
        lateinit var item: ScheduledRequest
        val job = launch(start = CoroutineStart.LAZY) {
            try {
                delay(request.at)
                log.record("arrive ${request.id} ${request.product}")
                val value = lookup.lookup(request.product)
                replies[index] = LookupReply(request.id, value.source.name, now(), "${value.value}")
            } catch (failure: LookupFailure) {
                replies[index] = LookupReply(request.id, "FAIL", now(), "${failure.code}")
            } catch (cancelled: CancellationException) {
                replies[index] = LookupReply(request.id, checkNotNull(item.stop).name, now())
                throw cancelled
            } finally {
                timers.forEach(Job::cancel)
            }
        }
        item = ScheduledRequest(job, timers)
        item
    }
    requests.forEachIndexed { index, request ->
        val item = scheduled[index]
        if (request.timeout != 0L) item.timers += scheduleStop(item, request.at + request.timeout, StopReason.TIMEOUT)
        if (request.cancelAt != 0L) item.timers += scheduleStop(item, request.cancelAt, StopReason.CANCELLED)
    }
    scheduled.forEach { it.job.start() }
    runUntilIdle()
    owner.cancel()
    runUntilIdle()
    val result = replies.map { checkNotNull(it).line() } +
        "LOOKUPS DONE ${now()} requests=${requests.size} calls=${services.calls}"
    return RunOutput(log.lines(), result.joinToString("\n"))
}

private fun VirtualTime.scheduleStop(item: ScheduledRequest, at: Long, reason: StopReason): Job = launch {
    delay(at)
    if (!item.job.isActive) return@launch
    item.stop = reason
    item.job.cancel()
}

private class FakeLookupServices(
    private val plans: Map<String, List<FetchPlan>>,
    private val log: EventLog,
    private val clock: () -> Long,
) : LookupServices {
    private val ordinal = HashMap<String, Int>()
    var calls = 0
        private set

    override fun now(): Long = clock()

    override suspend fun fetch(product: String): Int {
        val number = (ordinal[product] ?: 0) + 1
        ordinal[product] = number
        calls++
        val plan = plans.getValue(product).let { it[minOf(number, it.size) - 1] }
        val label = "$product $number"
        log.record("start fetch $label")
        log.awaitResponse("fetch $label", plan.latency)
        if (plan.failure != null) {
            log.record("fail fetch $label ${plan.failure}")
            throw LookupFailure(plan.failure)
        }
        val value = checkNotNull(plan.value)
        log.record("ok fetch $label $value")
        return value
    }
}
