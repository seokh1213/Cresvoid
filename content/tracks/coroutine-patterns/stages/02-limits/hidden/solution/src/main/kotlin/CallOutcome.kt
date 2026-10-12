import kotlinx.coroutines.withTimeoutOrNull

sealed interface CallOutcome {
    data class Answered(val value: Int) : CallOutcome

    data object ErrorResponse : CallOutcome

    data object TimedOut : CallOutcome

    data object DeadlinePassed : CallOutcome
}

class RequiredCallFailure(val reason: FailureReason) : RuntimeException("필수 호출이 $reason 로 끝났다")

suspend fun callWithin(timeout: Long, call: suspend () -> Int): CallOutcome =
    try {
        val value = withTimeoutOrNull(timeout) { call() }
        if (value == null) CallOutcome.TimedOut else CallOutcome.Answered(value)
    } catch (failure: ServiceFailure) {
        CallOutcome.ErrorResponse
    }

// withTimeoutOrNull 은 자기 시간 제한이 끝났을 때만 null 을 돌려주므로 안쪽 시간 초과와 마감 시각이 섞이지 않는다.
suspend fun beforeDeadline(deadline: Long, call: suspend () -> CallOutcome): CallOutcome =
    withTimeoutOrNull(deadline) { call() } ?: CallOutcome.DeadlinePassed

fun CallOutcome.requireValue(onError: FailureReason, onTimeout: FailureReason): Int = when (this) {
    is CallOutcome.Answered -> value
    CallOutcome.ErrorResponse -> throw RequiredCallFailure(onError)
    CallOutcome.TimedOut -> throw RequiredCallFailure(onTimeout)
    CallOutcome.DeadlinePassed -> throw RequiredCallFailure(FailureReason.DEADLINE)
}

fun CallOutcome.orDefault(): OptionalPart = when (this) {
    is CallOutcome.Answered -> OptionalPart.Present(value)
    CallOutcome.ErrorResponse -> OptionalPart.Missing(MissingCause.ERROR)
    CallOutcome.TimedOut -> OptionalPart.Missing(MissingCause.TIMEOUT)
    CallOutcome.DeadlinePassed -> OptionalPart.Missing(MissingCause.DEADLINE)
}
