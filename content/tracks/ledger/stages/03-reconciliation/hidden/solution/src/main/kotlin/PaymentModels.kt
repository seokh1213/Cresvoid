sealed interface RequestDetails {
    val currency: String
    val amount: Long
    val kind: String

    data class Pay(val debit: String, val credit: String, override val currency: String, override val amount: Long) : RequestDetails {
        override val kind: String get() = "PAY"
    }

    data class Refund(val original: String, override val currency: String, override val amount: Long) : RequestDetails {
        override val kind: String get() = "REFUND"
    }
}

enum class RequestState { READY, RETRYABLE, UNKNOWN, SUCCEEDED, FAILED }
enum class Observation { OK, DECLINE, RETRY, UNKNOWN, ABSENT }
enum class ScriptResult { OK, DECLINE, RETRY, TIMEOUT_OK, TIMEOUT_NONE }
enum class RemoteState { NONE, SUCCEEDED, FAILED }

class PaymentRequest(val details: RequestDetails) {
    var state: RequestState = RequestState.READY
        private set

    fun commit(observation: Observation) {
        state = when (observation) {
            Observation.OK -> RequestState.SUCCEEDED
            Observation.DECLINE -> RequestState.FAILED
            Observation.RETRY -> RequestState.RETRYABLE
            Observation.UNKNOWN -> RequestState.UNKNOWN
            Observation.ABSENT -> RequestState.READY
        }
    }
}
