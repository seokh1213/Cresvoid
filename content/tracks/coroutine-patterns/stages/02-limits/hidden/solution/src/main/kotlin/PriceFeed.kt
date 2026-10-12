import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

data class PriceEvent(val time: Long, val product: String, val version: Long, val change: PriceChange)

sealed interface PriceChange {
    data class ListPrice(val price: Int) : PriceChange

    data class Sale(val percent: Int) : PriceChange
}

class PriceCommands : CommandGroup {
    override val commands = setOf("EVENT", "WATCH", "AUDIT", "MONITOR", "PRICES")
    private val events = mutableListOf<PriceEvent>()
    private val subscribers = mutableListOf<Subscriber>()

    override fun execute(command: String, args: List<String>): RunOutput? {
        when (command) {
            "EVENT" -> events += parseEvent(args)
            "WATCH" -> addSubscriber(parseSubscriber(SubscriberKind.WATCH, args))
            "AUDIT" -> addSubscriber(parseSubscriber(SubscriberKind.AUDIT, args))
            "MONITOR" -> addSubscriber(parseSubscriber(SubscriberKind.MONITOR, args))
            else -> {
                args.requireSize(3, command)
                return runPrices(
                    product = parseName(args[0]),
                    initial = parseNumber(args[1], 0L..10_000_000L).toInt(),
                    stopTimeout = parseNumber(args[2]),
                )
            }
        }
        return null
    }

    private fun parseEvent(args: List<String>): PriceEvent {
        args.requireSize(5, "EVENT")
        val time = parseDuration(args[0])
        if (events.isNotEmpty() && time <= events.last().time) {
            throw ScenarioException("EVENT 의 시각은 앞 EVENT 보다 늦어야 한다")
        }
        val change = when (args[3]) {
            "LIST" -> PriceChange.ListPrice(parseNumber(args[4], 1L..10_000_000L).toInt())
            "SALE" -> PriceChange.Sale(parseNumber(args[4], 0L..90L).toInt())
            else -> throw ScenarioException("EVENT 의 종류는 LIST 나 SALE 이다: ${args[3]}")
        }
        return PriceEvent(time, parseName(args[1]), parseDuration(args[2]), change)
    }

    private fun parseSubscriber(kind: SubscriberKind, args: List<String>): Subscriber {
        args.requireSize(if (kind == SubscriberKind.AUDIT) 5 else 4, kind.name)
        return Subscriber(
            kind = kind,
            name = parseName(args[0]),
            from = parseNumber(args[1]),
            until = parseNumber(args[2]),
            work = parseDuration(args[3]),
            capacity = if (kind == SubscriberKind.AUDIT) parseNumber(args[4], 0L..100_000L).toInt() else 0,
        )
    }

    private fun addSubscriber(subscriber: Subscriber) {
        if (subscriber.name == SHARED_FEED) throw ScenarioException("수집자 이름으로 $SHARED_FEED 는 쓸 수 없다")
        if (subscribers.any { it.name == subscriber.name }) throw ScenarioException("이미 있는 수집자다: ${subscriber.name}")
        if (subscriber.from >= subscriber.until) throw ScenarioException("수집자의 끝 시각은 시작 시각보다 늦어야 한다")
        subscribers += subscriber
    }

    private fun runPrices(product: String, initial: Int, stopTimeout: Long): RunOutput {
        val plannedEvents = events.toList()
        val plannedSubscribers = subscribers.toList()
        events.clear()
        subscribers.clear()
        return VirtualTime.simulate {
            val log = EventLog(::now)
            val sharing = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
            val sharedFeed = liveFeed(SHARED_FEED, plannedEvents, log)
            val current = currentPrice(sharing, sharedFeed, product, initial, stopTimeout)
            for (subscriber in plannedSubscribers) {
                val prices = when (subscriber.kind) {
                    SubscriberKind.WATCH -> current
                    SubscriberKind.AUDIT ->
                        auditFeed(liveFeed(subscriber.name, plannedEvents, log), product, subscriber.capacity)
                    SubscriberKind.MONITOR -> monitorFeed(liveFeed(subscriber.name, plannedEvents, log), product)
                }
                follow(subscriber, prices, log)
            }
            runUntilIdle()
            val finalPrice = current.value
            sharing.cancel()
            runUntilIdle()
            RunOutput(log.lines(), "PRICES $product $finalPrice")
        }
    }
}

private const val SHARED_FEED = "shared"

private enum class SubscriberKind { WATCH, AUDIT, MONITOR }

private data class Subscriber(
    val kind: SubscriberKind,
    val name: String,
    val from: Long,
    val until: Long,
    val work: Long,
    val capacity: Int,
)

private fun VirtualTime.liveFeed(label: String, events: List<PriceEvent>, log: EventLog): Flow<PriceEvent> = flow {
    val openedAt = now()
    log.record("open $label")
    try {
        for (event in events) {
            if (event.time <= openedAt) continue
            delay(event.time - now())
            // 같은 시각에 처리를 마친 수집자가 먼저 값을 받도록 이벤트를 그 시각의 마지막 차례로 미룬다.
            yield()
            log.record("emit $label ${event.time}")
            emit(event)
        }
    } finally {
        log.record("close $label")
    }
}

private fun VirtualTime.follow(subscriber: Subscriber, prices: Flow<Int>, log: EventLog) {
    val collecting = launch {
        delay(subscriber.from)
        log.record("join ${subscriber.name}")
        prices.collect { price ->
            log.record("recv ${subscriber.name} $price")
            delay(subscriber.work)
        }
    }
    launch {
        delay(subscriber.until)
        collecting.cancel()
        log.record("leave ${subscriber.name}")
    }
}
