enum class CachePlacement {
    FRONT,
    INNER,
}

class CatalogSession(placement: CachePlacement, maxAttempts: Int) {
    private val store = ScriptedProductStore()
    private val cache: CachedProductClient
    private val retry: RetryingProductClient
    private val client: RecordedProductClient
    private val results = mutableListOf<String>()

    init {
        val catalog: ProductClient
        when (placement) {
            CachePlacement.FRONT -> {
                retry = RetryingProductClient(store, maxAttempts)
                cache = CachedProductClient(retry)
                catalog = cache
            }
            CachePlacement.INNER -> {
                cache = CachedProductClient(store)
                retry = RetryingProductClient(cache, maxAttempts)
                catalog = retry
            }
        }
        client = RecordedProductClient(catalog)
    }

    fun plan(sku: String, replies: List<StoreReply>) {
        store.plan(sku, replies)
    }

    fun get(sku: String) {
        record {
            client.get(sku)?.display() ?: "null"
        }
    }

    fun getMany(skus: List<String>) {
        record {
            client.getMany(skus).joinToString(",", "[", "]") {
                it?.display() ?: "null"
            }
        }
    }

    private fun record(query: () -> String) {
        val result = try {
            query()
        } catch (_: CatalogUnavailable) {
            "error"
        }
        results.add(result)
    }

    fun report(): String = buildString {
        append("results=")
        append(results.joinToString(";").ifEmpty { "-" })
        append(" | log=${client.singleCalls}/${client.batchCalls}")
        append(" cache=${cache.reads}/${cache.loads}")
        append(" retry=${retry.attempts} source=${store.reads}")
    }
}
