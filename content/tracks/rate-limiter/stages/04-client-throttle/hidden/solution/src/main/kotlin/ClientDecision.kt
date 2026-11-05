sealed interface ServerResponse {
    data object Accepted : ServerResponse

    data class TooManyRequests(val retryAfterSeconds: Long?) : ServerResponse

    data class Unavailable(val retryAfterSeconds: Long?) : ServerResponse
}

sealed interface ClientDecision {
    val draw: Long?

    data object Wait : ClientDecision {
        override val draw: Long? = null
    }

    data class Drop(override val draw: Long) : ClientDecision

    data class Sent(val response: ServerResponse, override val draw: Long) : ClientDecision

    data object Stats : ClientDecision {
        override val draw: Long? = null
    }
}

data class ClientObservation(
    val decision: ClientDecision,
    val counts: ClientCounts,
    val probability: DropProbability,
    val retryAt: Long,
)

fun clientLine(time: Long, name: String, observation: ClientObservation): String =
    with(observation) {
        "$time $name ${decision.word()} requests=${counts.requests} accepts=${counts.accepts} " +
            "p=${probability.numerator}/${probability.denominator} draw=${decision.draw ?: "-"} retry_at=$retryAt"
    }

private fun ClientDecision.word(): String = when (this) {
    ClientDecision.Wait -> "WAIT"
    is ClientDecision.Drop -> "DROP"
    ClientDecision.Stats -> "STATS"
    is ClientDecision.Sent -> when (response) {
        ServerResponse.Accepted -> "SERVER_200"
        is ServerResponse.TooManyRequests -> "SERVER_429"
        is ServerResponse.Unavailable -> "SERVER_503"
    }
}
