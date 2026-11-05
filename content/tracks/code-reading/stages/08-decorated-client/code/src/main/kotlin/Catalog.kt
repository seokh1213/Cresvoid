data class Product(val sku: String, val revision: Int) {
    fun display(): String = "$sku@$revision"
}

interface ProductClient {
    fun get(sku: String): Product?
    fun getMany(skus: List<String>): List<Product?>
}

class CatalogUnavailable : RuntimeException()

sealed interface StoreReply {
    data class Found(val revision: Int) : StoreReply
    data object Missing : StoreReply
    data object Unavailable : StoreReply
}

class ScriptedProductStore : ProductClient {
    private val replies = mutableMapOf<String, ArrayDeque<StoreReply>>()
    var reads = 0
        private set

    fun plan(sku: String, outcomes: List<StoreReply>) {
        replies[sku] = ArrayDeque(outcomes)
    }

    override fun get(sku: String): Product? {
        reads++
        val reply = replies[sku]?.removeFirstOrNull() ?: StoreReply.Missing
        return when (reply) {
            is StoreReply.Found -> Product(sku, reply.revision)
            StoreReply.Missing -> null
            StoreReply.Unavailable -> throw CatalogUnavailable()
        }
    }

    override fun getMany(skus: List<String>): List<Product?> = skus.map(::get)
}

fun parseReply(token: String): StoreReply = when (token) {
    "MISS" -> StoreReply.Missing
    "FAIL" -> StoreReply.Unavailable
    else -> StoreReply.Found(token.toInt().also { require(it > 0) })
}
