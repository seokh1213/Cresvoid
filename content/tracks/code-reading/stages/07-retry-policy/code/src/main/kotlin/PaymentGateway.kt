import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

sealed interface GatewayOutcome {
    data object Success : GatewayOutcome
    data object Timeout : GatewayOutcome
    data object ConnectionFailure : GatewayOutcome
    data class HttpError(val status: Int) : GatewayOutcome
}

fun parseGatewayOutcome(token: String): GatewayOutcome = when (token) {
    "OK" -> GatewayOutcome.Success
    "TIMEOUT" -> GatewayOutcome.Timeout
    "CONNECT" -> GatewayOutcome.ConnectionFailure
    else -> {
        require(token.startsWith("HTTP")) { "invalid-outcome" }
        val status = token.removePrefix("HTTP").toInt()
        require(status in 400..599) { "invalid-status" }
        GatewayOutcome.HttpError(status)
    }
}

class GatewayHttpException(val status: Int) : IOException("HTTP$status")

data class GatewayFailure(val reason: String, val retryable: Boolean)

fun callGateway(outcome: GatewayOutcome): GatewayFailure? = try {
    when (outcome) {
        GatewayOutcome.Success -> Unit
        GatewayOutcome.Timeout -> throw SocketTimeoutException("payment-timeout")
        GatewayOutcome.ConnectionFailure -> throw ConnectException("payment-connect")
        is GatewayOutcome.HttpError -> throw GatewayHttpException(outcome.status)
    }
    null
} catch (failure: SocketTimeoutException) {
    GatewayFailure("TIMEOUT", retryable = true)
} catch (failure: GatewayHttpException) {
    classifyHttpFailure(failure)
} catch (failure: IOException) {
    GatewayFailure("CONNECT", retryable = true)
}

private fun classifyHttpFailure(failure: GatewayHttpException): GatewayFailure {
    val retryable = when {
        failure.status == 429 -> true
        failure.status in 400..499 -> false
        else -> true
    }
    return GatewayFailure("HTTP${failure.status}", retryable)
}
