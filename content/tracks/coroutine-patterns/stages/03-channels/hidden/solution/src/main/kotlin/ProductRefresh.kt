import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class RefreshOutcome { SUCCEEDED, FAILED }

suspend fun refreshProduct(product: String, services: RefreshServices, policy: RefreshPolicy): RefreshOutcome {
    services.lock(product)
    try {
        return fetchWithRetry(product, services, policy)
    } finally {
        // 취소된 코루틴의 finally 에서는 중단 함수가 바로 취소 예외를 던지므로 해제 요청을 취소에서 떼어 낸다.
        withContext(NonCancellable) { services.unlock(product) }
    }
}

private suspend fun fetchWithRetry(product: String, services: RefreshServices, policy: RefreshPolicy): RefreshOutcome {
    for (attempt in 1..policy.maxAttempts) {
        when (attemptFetch(product, services, policy.attemptTimeout)) {
            AttemptResult.FETCHED -> return RefreshOutcome.SUCCEEDED
            AttemptResult.CLIENT_ERROR -> return RefreshOutcome.FAILED
            AttemptResult.SERVER_ERROR, AttemptResult.TIMED_OUT ->
                if (attempt < policy.maxAttempts) delay(policy.backoffAfter(attempt))
        }
    }
    return RefreshOutcome.FAILED
}

private enum class AttemptResult { FETCHED, CLIENT_ERROR, SERVER_ERROR, TIMED_OUT }

private val CLIENT_ERROR_CODES = 400..499

private suspend fun attemptFetch(product: String, services: RefreshServices, timeout: Long): AttemptResult =
    try {
        val answered = withTimeoutOrNull(timeout) { services.fetch(product) }
        if (answered == null) AttemptResult.TIMED_OUT else AttemptResult.FETCHED
    } catch (failure: FetchFailure) {
        if (failure.code in CLIENT_ERROR_CODES) AttemptResult.CLIENT_ERROR else AttemptResult.SERVER_ERROR
    }

private fun RefreshPolicy.backoffAfter(attempt: Int): Long =
    minOf(backoffBase * (1L shl (attempt - 1)), backoffMax)
