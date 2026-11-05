sealed interface StoreOperation {
    val counter: CounterKey
}

class SetCounter(override val counter: CounterKey, val value: Long) : StoreOperation

sealed interface CounterQuery : StoreOperation {
    val gateway: Gateway
}

class GetCounter(val request: SharedRequest) : CounterQuery {
    override val counter: CounterKey get() = request.counter
    override val gateway: Gateway get() = request.gateway
}

class IncrementCounter(val request: SharedRequest) : CounterQuery {
    override val counter: CounterKey get() = request.counter
    override val gateway: Gateway get() = request.gateway
}

class AddToCounter(val copy: LocalCopy, val amount: Long) : CounterQuery {
    override val counter: CounterKey get() = copy.counter
    override val gateway: Gateway get() = copy.gateway
}

sealed interface StoreReply {
    data class Value(val value: Long) : StoreReply

    data object Failure : StoreReply
}
