import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class DownstreamException(val service: String, val reason: String) : RuntimeException("$service $reason")

data class ServiceBehavior(val latencyMillis: Long = 100, val failureReason: String? = null)

class FakeDownstream {
    private val knownServices = setOf("price", "stock", "review", "counter", "recent")
    private val behaviors = mutableMapOf<String, ServiceBehavior>()

    fun slowDown(service: String, latencyMillis: Long) {
        behaviors[service] = behaviorOf(service).copy(latencyMillis = latencyMillis)
    }

    fun failWith(service: String, reason: String) {
        behaviors[service] = behaviorOf(service).copy(failureReason = reason)
    }

    suspend fun <T> respond(service: String, response: () -> T): T {
        val behavior = behaviorOf(service)
        delay(behavior.latencyMillis)
        behavior.failureReason?.let { throw DownstreamException(service, it) }
        return response()
    }

    private fun behaviorOf(service: String): ServiceBehavior {
        require(service in knownServices) { "unknown service: $service" }
        return behaviors[service] ?: ServiceBehavior()
    }
}

class DownstreamGateway(
    private val tracer: Tracer,
    private val downstream: FakeDownstream,
    connections: Int,
) {
    private var connectionPool = Semaphore(connections)

    fun resizePool(connections: Int) {
        connectionPool = Semaphore(connections)
    }

    suspend fun <T> call(service: String, response: () -> T): T = tracer.span(service) {
        connectionPool.withPermit { downstream.respond(service, response) }
    }
}

data class CatalogEntry(val priceWon: Long, val stock: Int, val ratings: List<Int>)

class Catalog {
    private val entries = mutableMapOf<String, CatalogEntry>()

    fun register(productId: String, entry: CatalogEntry) {
        require(entry.ratings.all { it in 1..5 }) { "rating must be between 1 and 5: ${entry.ratings}" }
        entries[productId] = entry
    }

    fun priceOf(productId: String): Long = entryOf("price", productId).priceWon

    fun stockOf(productId: String): Int = entryOf("stock", productId).stock

    fun ratingsOf(productId: String): List<Int> = entryOf("review", productId).ratings

    private fun entryOf(service: String, productId: String): CatalogEntry =
        entries[productId] ?: throw DownstreamException(service, "NOT_FOUND")
}
