import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

data class SentAlert(val sentAt: Long, val change: PriceChange)

class FakeAlertChannel(private val sendMillis: Long, private val clock: () -> Long) {
    private val sent = mutableListOf<SentAlert>()

    suspend fun send(change: PriceChange) {
        delay(sendMillis)
        sent += SentAlert(sentAt = clock(), change = change)
    }

    fun sentAlerts(): List<SentAlert> = sent.toList()
}

class PriceAlertHandler(private val channel: FakeAlertChannel) {
    fun start(changes: SharedFlow<PriceChange>, scope: CoroutineScope) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            changes
                .filter { it.isDrop }
                .collect { change -> channel.send(change) }
        }
    }
}
