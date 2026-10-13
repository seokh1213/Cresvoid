private class FlowStop(val token: Any) : RuntimeException(null, null, false, false)

class VirtualFlow<T>(private val produce: suspend (suspend (T) -> Unit) -> Unit) : RuntimeFlow<T> {
    override suspend fun collect(collector: suspend (T) -> Unit) { produce(collector) }

    override fun <R> map(transform: suspend (T) -> R): RuntimeFlow<R> = VirtualFlow { collector ->
        collect { value -> collector(transform(value)) }
    }

    override fun filter(predicate: suspend (T) -> Boolean): RuntimeFlow<T> = VirtualFlow { collector ->
        collect { value -> if (predicate(value)) collector(value) }
    }

    override fun take(count: Int): RuntimeFlow<T> {
        if (count == 0) return this
        return VirtualFlow { collector ->
            val token = Any()
            var received = 0
            try {
                collect { value ->
                    collector(value)
                    received++
                    if (received == count) throw FlowStop(token)
                }
            } catch (stop: FlowStop) {
                if (stop.token !== token) throw stop
            }
        }
    }
}
