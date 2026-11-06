class OrderService(private val repository: StockRepository) {
    @Synchronized
    fun place(productId: String, quantity: Int): Boolean {
        val stock = requireNotNull(repository.find(productId))
        val before = stock.remaining
        if (before < quantity) return false
        Thread.sleep(10)
        stock.remaining = before - quantity
        return true
    }
}
