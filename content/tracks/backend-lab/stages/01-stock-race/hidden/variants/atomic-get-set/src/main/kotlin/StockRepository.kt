import java.util.concurrent.atomic.AtomicInteger

class Stock(initial: Int) {
    val value = AtomicInteger(initial)
    var remaining: Int
        get() = value.get()
        set(updated) { value.set(updated) }
}

class StockRepository {
    private val products = mapOf(
        "warmup" to Stock(100),
        "sku-1" to Stock(100),
        "contract" to Stock(100)
    )

    fun find(productId: String): Stock? = products[productId]
}
