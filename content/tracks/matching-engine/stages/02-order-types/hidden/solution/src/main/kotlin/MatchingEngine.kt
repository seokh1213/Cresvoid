class MatchingEngine(private val output: (String) -> Unit) {
    val book = OrderBook()
    private val usedIds = HashSet<String>()
    private var arrivalClock = 0L
    private var tradeClock = 0L

    fun apply(command: Command.Mutation) {
        when (command) {
            is Command.Submit -> submit(command.request)
            is Command.Cancel -> cancel(command.id)
            is Command.Amend -> amend(command)
        }
    }

    private fun submit(request: OrderRequest) {
        if (!usedIds.add(request.id)) throw Rejected("DUPLICATE")
        val arrival = ++arrivalClock
        if (request.kind == OrderKind.FOK && !book.canFill(request)) {
            output("ORDER ${request.id} 0 ${request.quantity}")
            return
        }
        val remaining = match(request)
        val rests = request.kind == OrderKind.LIMIT
        if (rests && remaining > 0) {
            book.add(Order(request.id, request.owner, request.side, checkNotNull(request.price), remaining, arrival))
        }
        output("ORDER ${request.id} ${if (rests) remaining else 0} ${if (rests) 0 else remaining}")
    }

    private fun cancel(id: String) {
        val order = book.find(id) ?: throw Rejected("NOT_FOUND")
        book.remove(order)
        output("CANCEL $id ${order.remaining}")
    }

    private fun amend(command: Command.Amend) {
        val order = book.find(command.id) ?: throw Rejected("NOT_FOUND")
        val cancelled = (order.remaining - command.quantity).coerceAtLeast(0)
        val keepsPriority = command.quantity == 0L ||
            (command.price == order.price && command.quantity <= order.remaining)
        val resting = if (keepsPriority) {
            book.setRemaining(order, command.quantity)
            command.quantity
        } else {
            replace(order, command)
        }
        output("AMEND ${order.id} $resting $cancelled")
    }

    private fun replace(order: Order, command: Command.Amend): Long {
        book.remove(order)
        val arrival = ++arrivalClock
        val request = OrderRequest(order.id, order.owner, order.side, OrderKind.LIMIT, command.price, command.quantity)
        val remaining = match(request)
        if (remaining > 0) book.add(Order(order.id, order.owner, order.side, command.price, remaining, arrival))
        return remaining
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
