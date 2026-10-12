import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class PriceTag(val productId: String, val listPrice: Long, val salePrice: Long)

data class PriceChange(val productId: String, val before: Long, val after: Long) {
    val isDrop: Boolean
        get() = after < before
}

class PriceStore {
    private val priceTags = mutableMapOf<String, MutableStateFlow<PriceTag>>()
    private val changeEvents = MutableSharedFlow<PriceChange>(
        replay = 1,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val changes: SharedFlow<PriceChange> = changeEvents.asSharedFlow()

    fun register(productId: String, listPrice: Long) {
        require(productId !in priceTags) { "product already registered: $productId" }
        require(listPrice > 0) { "list price must be positive: $listPrice" }
        priceTags[productId] = MutableStateFlow(PriceTag(productId, listPrice, salePrice = listPrice))
    }

    fun priceOf(productId: String): StateFlow<PriceTag> = stateOf(productId).asStateFlow()

    fun changePrice(productId: String, salePrice: Long): Boolean {
        val state = stateOf(productId)
        val current = state.value
        require(salePrice in 1..current.listPrice) { "sale price $salePrice out of range for $productId" }
        val updated = current.copy(salePrice = salePrice)
        if (updated == current) return false
        state.value = updated
        changeEvents.tryEmit(PriceChange(productId, before = current.salePrice, after = updated.salePrice))
        return true
    }

    private fun stateOf(productId: String): MutableStateFlow<PriceTag> =
        requireNotNull(priceTags[productId]) { "unknown product: $productId" }
}
