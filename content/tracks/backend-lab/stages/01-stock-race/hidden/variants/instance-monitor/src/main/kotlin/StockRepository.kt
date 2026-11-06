class Stock(initial: Int) {
    @Volatile
    var remaining: Int = initial
}

class StockRepository {
    private val products = mapOf(
        "warmup" to Stock(100),
        "sku-1" to Stock(100),
        "contract" to Stock(100)
    )

    fun find(productId: String): Stock? = products[productId]
}
