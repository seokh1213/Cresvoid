interface DetailServices {
    suspend fun price(): Int

    suspend fun stock(): Int

    suspend fun review(): Int

    suspend fun coupon(price: Int): Int
}

class ServiceFailure(val call: String) : RuntimeException("$call 호출이 오류로 끝났다")

data class DetailLimits(
    val priceTimeout: Long,
    val stockTimeout: Long,
    val reviewTimeout: Long,
    val couponTimeout: Long,
    val deadline: Long,
)

sealed interface DetailResult {
    data class Loaded(
        val price: Int,
        val stock: Int,
        val review: OptionalPart,
        val coupon: OptionalPart,
    ) : DetailResult

    data class Failed(val reason: FailureReason) : DetailResult
}

sealed interface OptionalPart {
    data class Present(val value: Int) : OptionalPart

    data class Missing(val cause: MissingCause) : OptionalPart
}

enum class MissingCause { ERROR, TIMEOUT, DEADLINE }

enum class FailureReason { PRICE_ERROR, PRICE_TIMEOUT, STOCK_ERROR, STOCK_TIMEOUT, DEADLINE }

class DetailCommands : CommandGroup {
    override val commands = setOf("CALL", "TIMEOUT", "DEADLINE", "DETAIL")
    private val replies = mutableMapOf<DetailCall, Reply>()
    private val timeouts = mutableMapOf<DetailCall, Long>()
    private var deadline: Long? = null

    override fun execute(command: String, args: List<String>): RunOutput? {
        when (command) {
            "CALL" -> {
                if (args.size < 3) throw ScenarioException("CALL 의 인자 수가 맞지 않다")
                replies[DetailCall.parse(args[0])] = parseReply(args.drop(1))
            }
            "TIMEOUT" -> {
                args.requireSize(2, command)
                timeouts[DetailCall.parse(args[0])] = parseDuration(args[1])
            }
            "DEADLINE" -> {
                args.requireSize(1, command)
                deadline = parseDuration(args[0])
            }
            else -> {
                args.requireSize(0, command)
                return runDetail()
            }
        }
        return null
    }

    private fun parseReply(args: List<String>): Reply {
        val latency = parseDuration(args[0])
        return when {
            args.size == 2 && args[1] == "ERROR" -> Reply.Error(latency)
            args.size == 3 && args[1] == "OK" -> Reply.Value(latency, parseNumber(args[2]).toInt())
            else -> throw ScenarioException("CALL 의 응답은 OK <값> 이나 ERROR 다")
        }
    }

    private fun runDetail(): RunOutput {
        val scriptedReplies = DetailCall.entries.associateWith {
            replies[it] ?: throw ScenarioException("${it.label} 의 CALL 이 없다")
        }
        val limits = DetailLimits(
            priceTimeout = timeoutOf(DetailCall.PRICE),
            stockTimeout = timeoutOf(DetailCall.STOCK),
            reviewTimeout = timeoutOf(DetailCall.REVIEW),
            couponTimeout = timeoutOf(DetailCall.COUPON),
            deadline = deadline ?: throw ScenarioException("DEADLINE 이 없다"),
        )
        return VirtualTime.simulate {
            val log = EventLog(::now)
            val ending = runToEnd { loadProductDetail(FakeDetailServices(scriptedReplies, log), limits) }
            RunOutput(log.lines(), ending.toDetailLine())
        }
    }

    private fun timeoutOf(call: DetailCall): Long =
        timeouts[call] ?: throw ScenarioException("${call.label} 의 TIMEOUT 이 없다")
}

private enum class DetailCall(val label: String) {
    PRICE("price"),
    STOCK("stock"),
    REVIEW("review"),
    COUPON("coupon"),
    ;

    companion object {
        fun parse(token: String): DetailCall =
            entries.firstOrNull { it.label == token } ?: throw ScenarioException("서비스 이름이 아니다: $token")
    }
}

private sealed interface Reply {
    val latency: Long

    data class Value(override val latency: Long, val value: Int) : Reply

    data class Error(override val latency: Long) : Reply
}

private class FakeDetailServices(
    private val replies: Map<DetailCall, Reply>,
    private val log: EventLog,
) : DetailServices {
    override suspend fun price(): Int = respond(DetailCall.PRICE, "price")

    override suspend fun stock(): Int = respond(DetailCall.STOCK, "stock")

    override suspend fun review(): Int = respond(DetailCall.REVIEW, "review")

    override suspend fun coupon(price: Int): Int = respond(DetailCall.COUPON, "coupon $price")

    private suspend fun respond(call: DetailCall, request: String): Int {
        val reply = replies.getValue(call)
        log.record("start $request")
        log.awaitResponse(call.label, reply.latency)
        return when (reply) {
            is Reply.Value -> {
                log.record("ok ${call.label} ${reply.value}")
                reply.value
            }
            is Reply.Error -> {
                log.record("error ${call.label}")
                throw ServiceFailure(call.label)
            }
        }
    }
}

private fun Ending<DetailResult>.toDetailLine(): String = when (this) {
    is Ending.Returned -> when (val detail = value) {
        is DetailResult.Loaded ->
            "DETAIL OK $time price=${detail.price} stock=${detail.stock} " +
                "review=${detail.review.render()} coupon=${detail.coupon.render()}"
        is DetailResult.Failed -> "DETAIL FAIL $time ${detail.reason}"
    }
    is Ending.Cancelled -> "DETAIL CANCELLED $time"
    is Ending.Stuck -> "DETAIL STUCK $time"
}

private fun OptionalPart.render(): String = when (this) {
    is OptionalPart.Present -> value.toString()
    is OptionalPart.Missing -> cause.name
}
