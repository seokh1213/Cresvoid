import kotlinx.coroutines.test.runTest

private const val UNKNOWN_COMMAND = "ERR UNKNOWN_COMMAND"
private const val BAD_ARGUMENT = "ERR BAD_ARGUMENT"
private const val DUPLICATE_ORDER = "ERR DUPLICATE_ORDER"
private const val UNKNOWN_ORDER = "ERR UNKNOWN_ORDER"
private const val MAX_MILLIS = 100_000L

private val ORDER_ID_FORMAT = Regex("[A-Za-z0-9-]{1,20}")
private val MILLIS_FORMAT = Regex("[1-9][0-9]{0,5}")

fun main() {
    val commandLines = System.`in`.readBytes().decodeToString().split('\n')
    runTest {
        val origin = testScheduler.timeSource.markNow()
        val clock = { origin.elapsedNow().inWholeMilliseconds }
        val downstream = FakeDownstream(clock)
        val commands = OrderCommands(downstream, OrderProcessor(DownstreamClient(downstream), clock))
        for (line in commandLines) {
            commands.execute(line).forEach(::println)
        }
    }
}

class OrderCommands(private val downstream: FakeDownstream, private val processor: OrderProcessor) {
    private val results = mutableMapOf<String, OrderResult>()

    suspend fun execute(line: String): List<String> {
        val words = line.split(' ').filter(String::isNotEmpty)
        if (words.isEmpty()) return emptyList()
        val arguments = words.drop(1)
        return when (words.first()) {
            "REPLY" -> listOfNotNull(scriptReply(arguments))
            "ORDER" -> listOf(placeOrder(arguments))
            "TRACE" -> trace(arguments)
            else -> listOf(UNKNOWN_COMMAND)
        }
    }

    private fun scriptReply(arguments: List<String>): String? {
        if (arguments.size != 4) return BAD_ARGUMENT
        val (orderId, service, statusText, latencyText) = arguments
        val status = ReplyStatus.entries.firstOrNull { it.name == statusText } ?: return BAD_ARGUMENT
        val latency = parseMillis(latencyText) ?: return BAD_ARGUMENT
        if (!ORDER_ID_FORMAT.matches(orderId) || service !in downstream.services) return BAD_ARGUMENT
        downstream.script(orderId, service, ScriptedReply(status, latency))
        return null
    }

    private suspend fun placeOrder(arguments: List<String>): String {
        if (arguments.size != 2) return BAD_ARGUMENT
        val (orderId, timeLimitText) = arguments
        val timeLimit = parseMillis(timeLimitText) ?: return BAD_ARGUMENT
        if (!ORDER_ID_FORMAT.matches(orderId)) return BAD_ARGUMENT
        if (orderId in results) return DUPLICATE_ORDER
        val result = processor.process(orderId, timeLimit)
        results[orderId] = result
        return describe(result)
    }

    private fun trace(arguments: List<String>): List<String> {
        val orderId = arguments.singleOrNull() ?: return listOf(BAD_ARGUMENT)
        val result = results[orderId] ?: return listOf(UNKNOWN_ORDER)
        return downstream.requestsOf(orderId).map { describe(it, result.receivedAt) }
    }
}

private fun parseMillis(text: String): Long? =
    if (MILLIS_FORMAT.matches(text)) text.toLong().takeIf { it <= MAX_MILLIS } else null

private fun describe(result: OrderResult): String = when (val outcome = result.outcome) {
    is OrderOutcome.Confirmed -> {
        val confirmed = "${result.orderId} CONFIRMED ${result.elapsed}"
        if (outcome.skippedServices.isEmpty()) confirmed
        else "$confirmed SKIPPED ${outcome.skippedServices.joinToString(" ")}"
    }
    is OrderOutcome.Failed -> "${result.orderId} FAILED ${result.elapsed} ${outcome.service} ${outcome.reason}"
    OrderOutcome.TimedOut -> "${result.orderId} TIMEOUT ${result.elapsed}"
}

private fun describe(request: RequestRecord, orderReceivedAt: Long): String {
    val sentAt = request.sentAt - orderReceivedAt
    val endedAt = request.endedAt - orderReceivedAt
    return "${request.service}#${request.attempt} $sentAt-$endedAt ${request.outcome}"
}
