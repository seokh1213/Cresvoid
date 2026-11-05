fun OrderState.advance(event: OrderEvent): Transition = when (this) {
    OrderState.AwaitingPayment -> when (event.kind) {
        EventKind.PAY -> Transition.Applied(OrderState.Paid(event.occurredAt))
        EventKind.CANCEL -> Transition.Applied(OrderState.Cancelled(event.occurredAt))
        EventKind.SHIP -> Transition.Rejected("unpaid")
        EventKind.REFUND -> Transition.Rejected("unpaid")
    }
    is OrderState.Paid -> when (event.kind) {
        EventKind.SHIP -> if (event.occurredAt < paidAt) {
            Transition.Rejected("before-payment")
        } else {
            Transition.Applied(OrderState.Shipped(paidAt, event.occurredAt))
        }
        EventKind.REFUND -> if (event.occurredAt < paidAt) {
            Transition.Rejected("before-payment")
        } else {
            Transition.Applied(OrderState.Refunded(event.occurredAt))
        }
        EventKind.PAY -> Transition.Rejected("already-paid")
        EventKind.CANCEL -> Transition.Rejected("use-refund")
    }
    is OrderState.Shipped -> Transition.Rejected("shipped")
    is OrderState.Cancelled -> Transition.Rejected("cancelled")
    is OrderState.Refunded -> Transition.Rejected("refunded")
}

fun replayOrder(orderId: String, events: List<OrderEvent>): OrderReplay {
    var state: OrderState = OrderState.AwaitingPayment
    var nextSequence = 1
    val applied = mutableListOf<Int>()
    val rejected = mutableListOf<RejectedEvent>()
    val ordered = events.sortedBy { it.sequence }

    fun result(): OrderReplay = OrderReplay(
        orderId = orderId,
        state = state,
        applied = applied.toList(),
        rejected = rejected.toList(),
        nextSequence = nextSequence,
        pending = ordered.size - applied.size - rejected.size
    )

    ordered.forEach { event ->
        // Missing events may still arrive, so later sequence numbers must wait.
        if (event.sequence != nextSequence) return result()
        nextSequence++
        when (val transition = state.advance(event)) {
            is Transition.Rejected -> {
                rejected += RejectedEvent(event.sequence, transition.reason)
                return@forEach
            }
            is Transition.Applied -> state = transition.state
        }
        applied += event.sequence
    }
    return result()
}
