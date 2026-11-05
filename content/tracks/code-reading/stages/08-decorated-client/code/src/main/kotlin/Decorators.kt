class CachedProductClient(
    private val delegate: ProductClient,
) : ProductClient by delegate {
    private val products = mutableMapOf<String, Product?>()
    var reads = 0
        private set
    var loads = 0
        private set

    override fun get(sku: String): Product? {
        reads++
        return products.getOrPut(sku) {
            loads++
            delegate.get(sku)
        }
    }
}

class RetryingProductClient(
    private val delegate: ProductClient,
    private val maxAttempts: Int,
) : ProductClient by delegate {
    var attempts = 0
        private set

    init {
        require(maxAttempts > 0)
    }

    override fun get(sku: String): Product? {
        for (attempt in 1..maxAttempts) {
            attempts++
            try {
                return delegate.get(sku)
            } catch (failure: CatalogUnavailable) {
                if (attempt == maxAttempts) throw failure
            }
        }
        error("No catalog attempt completed")
    }

    override fun getMany(skus: List<String>): List<Product?> = skus.map(::get)
}

class RecordedProductClient(
    private val delegate: ProductClient,
) : ProductClient by delegate {
    var singleCalls = 0
        private set
    var batchCalls = 0
        private set

    override fun get(sku: String): Product? {
        singleCalls++
        return delegate.get(sku)
    }

    override fun getMany(skus: List<String>): List<Product?> {
        batchCalls++
        return delegate.getMany(skus)
    }
}
