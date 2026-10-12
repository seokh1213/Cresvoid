import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration

fun main() = runTest(timeout = Duration.INFINITE) {
    val origin = testScheduler.timeSource.markNow()
    val clock = { origin.elapsedNow().inWholeMilliseconds }
    val store = PriceStore()
    val supplierApi = FakeSupplierApi()
    val supplierSync = SupplierSync(
        supplierApi = supplierApi,
        store = store,
        ioDispatcher = StandardTestDispatcher(testScheduler, name = "supplier-io"),
        clock = clock,
    )
    val screens = mutableMapOf<String, ProductScreen>()
    val alertChannels = mutableMapOf<String, FakeAlertChannel>()

    for (command in generateSequence(::readlnOrNull).map(String::trim).filter(String::isNotEmpty)) {
        val words = command.split(Regex("\\s+"))
        try {
            when (words[0]) {
                "PRODUCT" -> store.register(productId = words[1], listPrice = words[2].toLong())
                "PRICE" -> store.changePrice(productId = words[1], salePrice = words[2].toLong())
                "OPEN" -> {
                    require(words[1] !in screens) { "screen already open: ${words[1]}" }
                    val screen = ProductScreen(clock)
                    screen.open(store.priceOf(productId = words[2]), backgroundScope)
                    screens[words[1]] = screen
                }
                "ALERT" -> {
                    require(words[1] !in alertChannels) { "channel already on: ${words[1]}" }
                    val channel = FakeAlertChannel(sendMillis = words[2].toLong(), clock = clock)
                    PriceAlertHandler(channel).start(store.changes, backgroundScope)
                    alertChannels[words[1]] = channel
                }
                "SUPPLY" -> supplierApi.publish(supplierId = words[1], lines = words.drop(2))
                "DROP" -> supplierApi.disconnectNextRequest(supplierId = words[1], afterLines = words[2].toInt())
                "SYNC" -> println(formatReport(supplierSync.sync(supplierId = words[1])))
                "WAIT" -> delay(words[1].toLong())
                "SCREEN" -> {
                    val screen = requireNotNull(screens[words[1]]) { "unknown screen: ${words[1]}" }
                    println(formatScreen(words[1], screen.sentUpdates()))
                }
                "SENT" -> {
                    val channel = requireNotNull(alertChannels[words[1]]) { "unknown channel: ${words[1]}" }
                    println(formatAlerts(words[1], channel.sentAlerts()))
                }
                else -> println("ERROR unknown command ${words[0]}")
            }
        } catch (error: IllegalArgumentException) {
            println("ERROR ${error.message}")
        } catch (error: IndexOutOfBoundsException) {
            println("ERROR missing argument: $command")
        }
    }
}

private fun formatScreen(screenId: String, updates: List<ScreenUpdate>): String =
    updates.joinToString(" ", prefix = "$screenId ") { "${it.salePrice}@${it.sentAt}" }

private fun formatAlerts(channelName: String, alerts: List<SentAlert>): String =
    if (alerts.isEmpty()) {
        "$channelName none"
    } else {
        alerts.joinToString(" ", prefix = "$channelName ") { "${it.change.productId}:${it.change.after}@${it.sentAt}" }
    }

private fun formatReport(report: SyncReport): String = buildString {
    append("${report.supplierId} requests=${report.requests} changed=${report.changed} unchanged=${report.unchanged}")
    append(" ${report.elapsedMillis}ms")
    report.stopReason?.let { append(" stopped: $it") }
}
