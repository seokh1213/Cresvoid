data class ProductDetail(val productId: String, val revision: String) {
    init {
        require(productId.matches(Regex("[A-Za-z0-9_-]+"))) { "invalid product id" }
        require(revision.matches(Regex("[A-Za-z0-9_-]+"))) { "invalid revision" }
    }
}

data class CachedDetail(val detail: ProductDetail, val expiresAt: Long) {
    fun isExpired(now: Long): Boolean = now >= expiresAt

    fun describe(): String = "${detail.productId}@${detail.revision}:$expiresAt"
}

class CatalogClock {
    var now: Long = 0
        private set

    fun advance(seconds: Long) {
        require(seconds >= 0) { "negative advance" }
        now = Math.addExact(now, seconds)
    }

    fun deadline(ttl: Long): Long {
        require(ttl > 0) { "non-positive ttl" }
        return Math.addExact(now, ttl)
    }
}

data class CacheReport(
    val now: Long,
    val hits: Int,
    val misses: Int,
    val evictions: Int,
    val last: String,
    val order: List<String>,
) {
    fun format(): String =
        "time=$now hits=$hits misses=$misses evictions=$evictions " +
            "last=$last order=[${order.joinToString(",")}]"
}
