import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

const val SCREEN_REFRESH_MILLIS = 50L

data class ScreenUpdate(val sentAt: Long, val salePrice: Long)

class ProductScreen(private val clock: () -> Long) {
    private val updates = mutableListOf<ScreenUpdate>()

    fun open(prices: StateFlow<PriceTag>, scope: CoroutineScope) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            prices.collect { tag ->
                updates += ScreenUpdate(sentAt = clock(), salePrice = tag.salePrice)
                delay(SCREEN_REFRESH_MILLIS)
            }
        }
    }

    fun sentUpdates(): List<ScreenUpdate> = updates.toList()
}
