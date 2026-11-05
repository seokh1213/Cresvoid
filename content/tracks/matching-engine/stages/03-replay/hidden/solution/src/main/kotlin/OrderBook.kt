import java.util.TreeMap

class OrderBook {
    private class PriceLevel {
        val orders = LinkedHashMap<String, Order>()
        var quantity = 0L
    }

    private val bids = TreeMap<Long, PriceLevel>(reverseOrder())
    private val asks = TreeMap<Long, PriceLevel>()
    private val active = HashMap<String, Order>()

    val size: Int get() = active.size

    fun find(id: String): Order? = active[id]

    fun add(order: Order) {
        val level = levels(order.side).getOrPut(order.price) { PriceLevel() }
        level.orders[order.id] = order
        level.quantity += order.remaining
        active[order.id] = order
    }

    fun remove(order: Order) {
        val levels = levels(order.side)
        val level = levels.getValue(order.price)
        level.quantity -= order.remaining
        level.orders.remove(order.id)
        active.remove(order.id)
        if (level.orders.isEmpty()) levels.remove(order.price)
    }

    fun setRemaining(order: Order, quantity: Long) {
        if (quantity == 0L) {
            remove(order)
            order.remaining = 0
            return
        }
        levels(order.side).getValue(order.price).quantity += quantity - order.remaining
        order.remaining = quantity
    }

    fun bestMaker(takerSide: Side): Order? =
        opposite(takerSide).firstEntry()?.value?.orders?.values?.first()

    fun canFill(request: OrderRequest): Boolean {
        var needed = request.quantity
        for ((price, level) in opposite(request.side)) {
            if (!crosses(request.side, request.price, price)) break
            needed -= level.quantity
            if (needed <= 0) return true
        }
        return false
    }

    fun render(): String = "BOOK B ${renderSide(bids)} S ${renderSide(asks)}"

    fun byArrival(): List<Order> = active.values.sortedBy { it.arrival }

    private fun renderSide(levels: TreeMap<Long, PriceLevel>): String =
        if (levels.isEmpty()) "-" else levels.entries.joinToString(",") { (price, level) -> "$price:${level.quantity}" }

    private fun levels(side: Side): TreeMap<Long, PriceLevel> = if (side == Side.BUY) bids else asks
    private fun opposite(side: Side): TreeMap<Long, PriceLevel> = if (side == Side.BUY) asks else bids
}

fun crosses(side: Side, limit: Long?, makerPrice: Long): Boolean =
    limit == null || if (side == Side.BUY) makerPrice <= limit else makerPrice >= limit
