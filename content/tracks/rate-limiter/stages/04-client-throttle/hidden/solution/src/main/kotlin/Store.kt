data class CounterKey(val limiter: SharedLimiter, val key: String, val window: Long)

class Store {
    private val counters = HashMap<CounterKey, Long>()
    private var running = true

    fun stop() {
        running = false
    }

    fun start() {
        running = true
    }

    fun valueOf(counter: CounterKey): Long = counters[counter] ?: 0

    fun write(operation: SetCounter) {
        if (running) counters[operation.counter] = operation.value
    }

    fun answer(query: CounterQuery): StoreReply {
        if (!running) return StoreReply.Failure
        val value = when (query) {
            is GetCounter -> valueOf(query.counter)
            is IncrementCounter -> addTo(query.counter, 1)
            is AddToCounter -> addTo(query.counter, query.amount)
        }
        return StoreReply.Value(value)
    }

    private fun addTo(counter: CounterKey, amount: Long): Long {
        val total = valueOf(counter) + amount
        counters[counter] = total
        return total
    }
}
