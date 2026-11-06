class OrderService(private val repository: StockRepository) {
    fun place(productId: String, quantity: Int): Boolean {
        val stock = requireNotNull(repository.find(productId))
        // Both service instances must use the same product monitor.
        return synchronized(stock) {
            val before = stock.remaining
            if (before < quantity) return@synchronized false
            Thread.sleep(10)
            stock.remaining = before - quantity
            true
        }
    }
}
