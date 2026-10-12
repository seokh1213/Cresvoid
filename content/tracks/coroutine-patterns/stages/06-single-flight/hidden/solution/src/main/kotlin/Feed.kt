interface FeedServices {
    suspend fun fetch(item: String)

    suspend fun transform(item: String): TransformOutcome

    suspend fun store(item: String)
}

enum class TransformOutcome { TRANSFORMED, SKIPPED }

class TransformFailure(val item: String) : RuntimeException("$item 변환이 실패했다")

data class PipelineSettings(val workers: Int, val capacity: Int)

sealed interface PipelineResult {
    data class Completed(val stored: Int, val skipped: Int) : PipelineResult

    data class Failed(val item: String) : PipelineResult
}

class FeedCommands : CommandGroup {
    override val commands = setOf("FEED", "PIPELINE")
    private val plansByItem = LinkedHashMap<String, FeedPlan>()

    override fun execute(command: String, args: List<String>): RunOutput? {
        if (command == "FEED") {
            addItem(args)
            return null
        }
        args.requireSize(2, command)
        val settings = PipelineSettings(
            workers = parseNumber(args[0], 1L..10_000L).toInt(),
            capacity = parseNumber(args[1], 0L..100_000L).toInt(),
        )
        return runPipeline(settings)
    }

    private fun addItem(args: List<String>) {
        args.requireSize(5, "FEED")
        val item = parseName(args[0])
        if (item in plansByItem) throw ScenarioException("이미 있는 상품이다: $item")
        val outcome = TransformPlan.entries.firstOrNull { it.name == args[3] }
            ?: throw ScenarioException("변환 결과는 OK, SKIP, FAIL 가운데 하나다: ${args[3]}")
        plansByItem[item] = FeedPlan(
            fetch = parseDuration(args[1]),
            transform = parseDuration(args[2]),
            outcome = outcome,
            store = parseDuration(args[4]),
        )
    }

    private fun runPipeline(settings: PipelineSettings): RunOutput {
        if (plansByItem.isEmpty()) throw ScenarioException("PIPELINE 앞에 FEED 가 없다")
        val plans = plansByItem.toMap()
        plansByItem.clear()
        return VirtualTime.simulate {
            val log = EventLog(::now)
            val services = FakeFeedServices(plans, log)
            val ending = runToEnd { runFeedPipeline(plans.keys.toList(), services, settings) }
            RunOutput(log.lines(), ending.toPipelineLine())
        }
    }
}

private enum class TransformPlan { OK, SKIP, FAIL }

private data class FeedPlan(val fetch: Long, val transform: Long, val outcome: TransformPlan, val store: Long)

private class FakeFeedServices(
    private val plans: Map<String, FeedPlan>,
    private val log: EventLog,
) : FeedServices {
    override suspend fun fetch(item: String) {
        log.record("start fetch $item")
        log.awaitResponse("fetch $item", plans.getValue(item).fetch)
        log.record("ok fetch $item")
    }

    override suspend fun transform(item: String): TransformOutcome {
        val plan = plans.getValue(item)
        log.record("start transform $item")
        log.awaitResponse("transform $item", plan.transform)
        return when (plan.outcome) {
            TransformPlan.OK -> {
                log.record("ok transform $item")
                TransformOutcome.TRANSFORMED
            }
            TransformPlan.SKIP -> {
                log.record("skip transform $item")
                TransformOutcome.SKIPPED
            }
            TransformPlan.FAIL -> {
                log.record("fail transform $item")
                throw TransformFailure(item)
            }
        }
    }

    override suspend fun store(item: String) {
        log.record("start store $item")
        log.awaitResponse("store $item", plans.getValue(item).store)
        log.record("ok store $item")
    }
}

private fun Ending<PipelineResult>.toPipelineLine(): String = when (this) {
    is Ending.Returned -> when (val result = value) {
        is PipelineResult.Completed -> "PIPELINE DONE $time stored=${result.stored} skipped=${result.skipped}"
        is PipelineResult.Failed -> "PIPELINE FAIL $time ${result.item}"
    }
    is Ending.Cancelled -> "PIPELINE CANCELLED $time"
    is Ending.Stuck -> "PIPELINE STUCK $time"
}
