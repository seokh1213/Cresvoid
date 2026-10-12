class SharedRequest(val number: Long, val gateway: Gateway, val counter: CounterKey)

sealed interface RequestResult {
    data class Allow(val count: Long) : RequestResult

    data class Deny(val count: Long) : RequestResult

    data object Open : RequestResult

    data object Shed : RequestResult
}
