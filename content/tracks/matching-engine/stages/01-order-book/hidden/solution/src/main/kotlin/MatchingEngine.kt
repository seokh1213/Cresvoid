class MatchingEngine(private val output: (String) -> Unit) {
    val book = OrderBook()
    private val usedIds = HashSet<String>()
    private var arrivalClock = 0L
    private var tradeClock = 0L

    fun apply(command: Command.Mutation) {
        when (command) {
            is Command.Submit -> submit(command.request)
            is Command.Cancel -> cancel(command.id)
        }
    }

    private fun submit(request: OrderRequest) {
        if (!usedIds.add(request.id)) throw Rejected("DUPLICATE")
        val arrival = ++arrivalClock
        val remaining = match(request)
        if (remaining > 0) {
            book.add(Order(request.id, request.owner, request.side, request.price, remaining, arrival))
        }
        output("ORDER ${request.id} $remaining 0")
    }

    private fun cancel(id: String) {
        val order = book.find(id) ?: throw Rejected("NOT_FOUND")
        book.remove(order)
        output("CANCEL $id ${order.remaining}")
    }

    private fun match(request: OrderRequest): Long {
        var remaining = request.quantity
        while (remaining > 0) {
            val maker = book.bestMaker(request.side) ?: break
            if (!crosses(request.side, request.price, maker.price)) break
            val quantity = minOf(remaining, maker.remaining)
            remaining -= quantity
            book.setRemaining(maker, maker.remaining - quantity)
            val buyId = if (request.side == Side.BUY) request.id else maker.id
            val sellId = if (request.side == Side.SELL) request.id else maker.id
            output("TRADE ${++tradeClock} $buyId $sellId ${maker.price} $quantity")
        }
        return remaining
    }
}
