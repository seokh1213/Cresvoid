package lab

fun <T> retry(maxAttempts: Int, trace: RetryTrace, transaction: () -> T): T {
    require(maxAttempts in 1..5)
    return transaction()
}
