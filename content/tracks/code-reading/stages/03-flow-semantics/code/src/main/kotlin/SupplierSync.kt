import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retry

const val FETCH_MILLIS_PER_LINE = 40L
const val APPLY_INTERVAL_MILLIS = 15L

class SupplierDisconnectedException(supplierId: String) : RuntimeException("$supplierId disconnected")

class MalformedPriceLineException(line: String) : RuntimeException("malformed price line $line")

data class SupplierPrice(val productId: String, val salePrice: Long)

data class SyncReport(
    val supplierId: String,
    val requests: Int,
    val changed: Int,
    val unchanged: Int,
    val elapsedMillis: Long,
    val stopReason: String?,
)

class FakeSupplierApi {
    private val priceLists = mutableMapOf<String, List<String>>()
    private val pendingDisconnects = mutableMapOf<String, Int>()

    fun publish(supplierId: String, lines: List<String>) {
        priceLists[supplierId] = lines
    }

    fun disconnectNextRequest(supplierId: String, afterLines: Int) {
        pendingDisconnects[supplierId] = afterLines
    }

    fun priceList(supplierId: String): Flow<String> {
        val lines = requireNotNull(priceLists[supplierId]) { "unknown supplier: $supplierId" }
        return flow {
            val disconnectAfterLines = pendingDisconnects.remove(supplierId)
            for ((index, line) in lines.withIndex()) {
                delay(FETCH_MILLIS_PER_LINE)
                if (index == disconnectAfterLines) throw SupplierDisconnectedException(supplierId)
                emit(line)
            }
        }
    }
}

class SupplierSync(
    private val supplierApi: FakeSupplierApi,
    private val store: PriceStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
) {
    suspend fun sync(supplierId: String): SyncReport {
        val startedAt = clock()
        var requests = 0
        var changed = 0
        var unchanged = 0
        var stopReason: String? = null
        supplierApi.priceList(supplierId)
            .onStart { requests += 1 }
            .map(::parseSupplierPrice)
            .retry(1) { it is SupplierDisconnectedException }
            .flowOn(ioDispatcher)
            .catch { failure -> stopReason = failure.message }
            .collect { price ->
                if (store.changePrice(price.productId, price.salePrice)) changed += 1 else unchanged += 1
                delay(APPLY_INTERVAL_MILLIS)
            }
        return SyncReport(
            supplierId = supplierId,
            requests = requests,
            changed = changed,
            unchanged = unchanged,
            elapsedMillis = clock() - startedAt,
            stopReason = stopReason,
        )
    }
}

private fun parseSupplierPrice(line: String): SupplierPrice {
    val productId = line.substringBefore(':', missingDelimiterValue = "")
    val salePrice = line.substringAfter(':', missingDelimiterValue = "").toLongOrNull()
    if (productId.isEmpty() || salePrice == null) throw MalformedPriceLineException(line)
    return SupplierPrice(productId = productId, salePrice = salePrice)
}
