import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

const val PAGE_TIMEOUT_MILLIS = 400L
const val STOCK_TIMEOUT_MILLIS = 200L

data class RatingSummary(val average: Double, val count: Int)

data class ProductPage(val productId: String, val priceWon: Long, val stock: Int?, val rating: RatingSummary?)

sealed interface PageResult {
    data class Shown(val page: ProductPage) : PageResult

    data class Failed(val productId: String, val reason: String) : PageResult
}

private class PageParts(val priceWon: Long, val stock: Int?, val ratings: List<Int>?)

class ProductPageService(
    private val gateway: DownstreamGateway,
    private val catalog: Catalog,
    private val tracer: Tracer,
    private val viewTracker: ViewTracker,
    private val computeDispatcher: CoroutineDispatcher,
) {
    suspend fun view(requestId: String, productId: String): PageResult =
        withContext(tracer.context(requestId)) {
            try {
                val parts = withTimeout(PAGE_TIMEOUT_MILLIS) { fetchParts(productId) }
                val page = withContext(computeDispatcher) {
                    tracer.span("summary") { summarize(productId, parts) }
                }
                viewTracker.record(productId)
                PageResult.Shown(page)
            } catch (failure: DownstreamException) {
                PageResult.Failed(productId, "${failure.service} ${failure.reason}")
            } catch (timeout: TimeoutCancellationException) {
                PageResult.Failed(productId, "timeout")
            }
        }

    private suspend fun fetchParts(productId: String): PageParts = supervisorScope {
        val stock = async {
            withTimeoutOrNull(STOCK_TIMEOUT_MILLIS) { gateway.call("stock") { catalog.stockOf(productId) } }
        }
        val ratings = async { gateway.call("review") { catalog.ratingsOf(productId) } }
        val priceWon = gateway.call("price") { catalog.priceOf(productId) }
        PageParts(priceWon, stock.awaitOptional(), ratings.awaitOptional())
    }

    private fun summarize(productId: String, parts: PageParts): ProductPage = ProductPage(
        productId = productId,
        priceWon = parts.priceWon,
        stock = parts.stock,
        rating = parts.ratings?.takeIf { it.isNotEmpty() }?.let { RatingSummary(it.average(), it.size) },
    )
}

private suspend fun <T> Deferred<T>.awaitOptional(): T? =
    try {
        await()
    } catch (failure: DownstreamException) {
        null
    }
