class OrderService(private val repository: StockRepository) {
    fun place(productId: String, quantity: Int): Boolean {
        val stock = requireNotNull(repository.find(productId))
        val before = stock.value.get()
        if (before < quantity) return false
        Thread.sleep(10)
        stock.value.set(before - quantity)
        return true
    }
}
