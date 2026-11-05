import java.util.LinkedHashMap

class ProductCache(private val capacity: Int, private val clock: CatalogClock) {
    init {
        require(capacity > 0) { "non-positive capacity" }
    }

    private var hits = 0
    private var misses = 0
    private var evictions = 0
    private var last = "NONE"

    private val entries = object : LinkedHashMap<String, CachedDetail>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDetail>): Boolean {
            if (size <= capacity) return false
            evictions++
            return true
        }
    }

    fun put(productId: String, revision: String, ttl: Long) {
        entries[productId] = newEntry(productId, revision, ttl)
    }

    fun read(productId: String) {
        val cached = entries[productId]
        if (cached == null) {
            recordMiss(productId)
            return
        }
        if (cached.isExpired(clock.now)) {
            entries.remove(productId)
            recordMiss(productId)
            return
        }
        hits++
        last = "HIT:${cached.detail.productId}@${cached.detail.revision}"
    }

    fun probe(productId: String) {
        last = if (productId in entries) "PRESENT:$productId" else "ABSENT:$productId"
    }

    fun revision(productId: String) {
        val cached = entries[productId]
        val revision = cached?.detail?.revision ?: "-"
        last = "REVISION:$productId@$revision"
    }

    fun warm(productId: String, revision: String, ttl: Long) {
        val cached = entries.getOrPut(productId) {
            newEntry(productId, revision, ttl)
        }
        if (cached.isExpired(clock.now)) {
            entries[productId] = newEntry(productId, revision, ttl)
        }
    }

    fun invalidate(productId: String) {
        entries.remove(productId)
    }

    fun sweep() {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val cached = iterator.next().value
            if (cached.isExpired(clock.now)) iterator.remove()
        }
    }

    fun report(): CacheReport = CacheReport(
        now = clock.now,
        hits = hits,
        misses = misses,
        evictions = evictions,
        last = last,
        order = snapshot(),
    )

    private fun snapshot(): List<String> = entries.entries.map { it.value.describe() }

    private fun newEntry(productId: String, revision: String, ttl: Long): CachedDetail =
        CachedDetail(ProductDetail(productId, revision), clock.deadline(ttl))

    private fun recordMiss(productId: String) {
        misses++
        last = "MISS:$productId"
    }
}
