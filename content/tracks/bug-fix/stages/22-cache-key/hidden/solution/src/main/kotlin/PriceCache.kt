class CatalogClock {
    var now: Long = 0
        private set

    fun advance(seconds: Long): Long {
        now += seconds
        return now
    }
}

private data class CachedPrice(
    val productId: String,
    val amount: Long,
    val expiresAt: Long,
)

class PriceCache(private val clock: CatalogClock) {
    private val entries = mutableMapOf<String, CachedPrice>()

    fun find(request: PriceRequest): Long? {
        val key = request.cacheKey()
        val entry = entries[key] ?: return null
        if (clock.now >= entry.expiresAt) {
            entries.remove(key)
            return null
        }
        return entry.amount
    }

    fun store(request: PriceRequest, product: Product, amount: Long) {
        entries[request.cacheKey()] = CachedPrice(
            productId = product.id,
            amount = amount,
            expiresAt = clock.now + product.ttlSeconds,
        )
    }

    fun invalidate(productId: String) {
        entries.entries.removeIf { it.value.productId == productId }
    }
}
