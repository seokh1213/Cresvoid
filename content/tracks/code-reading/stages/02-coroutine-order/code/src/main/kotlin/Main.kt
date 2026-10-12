import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.util.Locale
import kotlin.time.Duration

data class CompletedView(val requestId: String, val result: PageResult, val elapsedMillis: Long)

fun main() = runTest(timeout = Duration.INFINITE) {
    val origin = testScheduler.timeSource.markNow()
    val clock = { origin.elapsedNow().inWholeMilliseconds }
    val tracer = Tracer(clock)
    val catalog = Catalog()
    val downstream = FakeDownstream()
    val gateway = DownstreamGateway(tracer, downstream, connections = 4)
    val viewTracker = ViewTracker(gateway, StandardTestDispatcher(testScheduler), ::println)
    val pageService = ProductPageService(gateway, catalog, tracer, viewTracker, Dispatchers.Default)
    var viewCount = 0
    var lastView: CompletedView? = null

    for (command in generateSequence(::readlnOrNull).map(String::trim).filter(String::isNotEmpty)) {
        val words = command.split(Regex("\\s+"))
        try {
            when (words[0]) {
                "PRODUCT" -> catalog.register(
                    productId = words[1],
                    entry = CatalogEntry(
                        priceWon = words[2].toLong(),
                        stock = words[3].toInt(),
                        ratings = words.drop(4).map(String::toInt),
                    ),
                )
                "SLOW" -> downstream.slowDown(words[1], words[2].toLong())
                "FAIL" -> downstream.failWith(words[1], words[2])
                "POOL" -> gateway.resizePool(words[1].toInt())
                "VIEW" -> {
                    viewCount += 1
                    val requestId = "view-$viewCount"
                    val startedAt = clock()
                    val result = pageService.view(requestId, productId = words[1])
                    lastView = CompletedView(requestId, result, elapsedMillis = clock() - startedAt)
                    viewTracker.awaitPending()
                }
                "PAGE" -> println(lastView?.let(::formatPage) ?: "NO VIEW")
                "TRACE" -> println(lastView?.let { formatTrace(tracer.spansOf(it.requestId)) } ?: "NO VIEW")
                else -> println("ERROR unknown command ${words[0]}")
            }
        } catch (error: IllegalArgumentException) {
            println("ERROR ${error.message}")
        } catch (error: IndexOutOfBoundsException) {
            println("ERROR missing argument: $command")
        }
    }
}

private fun formatPage(view: CompletedView): String = when (val result = view.result) {
    is PageResult.Shown -> with(result.page) {
        val stockText = stock?.toString() ?: "?"
        val ratingText = rating?.let { String.format(Locale.ROOT, "%.1f(%d)", it.average, it.count) } ?: "?"
        "$productId price=$priceWon stock=$stockText rating=$ratingText ${view.elapsedMillis}ms"
    }
    is PageResult.Failed -> "${result.productId} failed ${result.reason} ${view.elapsedMillis}ms"
}

private fun formatTrace(spans: List<Span>): String =
    spans.joinToString(" ") { "${it.name}:${it.startMillis}-${it.endMillis}:${it.outcome.name.lowercase()}" }
