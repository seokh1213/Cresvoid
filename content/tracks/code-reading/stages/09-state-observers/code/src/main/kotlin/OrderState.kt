enum class OrderAction {
    PAY,
    SHIP,
    CANCEL
}

data class OrderRequest(val action: OrderAction, val reference: String)

interface OrderState {
    val code: String
    fun next(action: OrderAction): OrderState?
}

object CreatedState : OrderState {
    override val code = "CREATED"

    override fun next(action: OrderAction): OrderState? = when (action) {
        OrderAction.PAY -> PaidState
        OrderAction.SHIP -> null
        OrderAction.CANCEL -> CancelledState
    }
}

object PaidState : OrderState {
    override val code = "PAID"

    override fun next(action: OrderAction): OrderState? = when (action) {
        OrderAction.PAY -> null
        OrderAction.SHIP -> ShippedState
        OrderAction.CANCEL -> CancelledState
    }
}

object ShippedState : OrderState {
    override val code = "SHIPPED"

    override fun next(action: OrderAction): OrderState? = null
}

object CancelledState : OrderState {
    override val code = "CANCELLED"

    override fun next(action: OrderAction): OrderState? = null
}

enum class RejectionPolicy {
    IGNORE,
    REJECT
}

class RejectedTransition(val orderId: String, val action: OrderAction, val stateCode: String) :
    IllegalStateException("$orderId:${action.name}@$stateCode")

data class OrderChange(
    val orderId: String,
    val version: Int,
    val before: OrderState,
    val after: OrderState
)
