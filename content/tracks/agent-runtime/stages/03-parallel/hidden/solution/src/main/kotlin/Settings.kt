data class Settings(
    val maxTurns: Int = 10,
    val replyLimit: Int = 1_000,
    val replyLimitCap: Int = 4_000,
    val repeatLimit: Int = 0,
    val concurrency: Int = 4,
    val timeoutMs: Int = 0,
    val retries: Int = 0,
    val backoffMs: Int = 0,
    val trace: Boolean = false,
) {
    val isConsistent: Boolean get() = replyLimit <= replyLimitCap

    fun with(change: SettingsChange): Settings = Settings(
        maxTurns = change.maxTurns ?: maxTurns,
        replyLimit = change.replyLimit ?: replyLimit,
        replyLimitCap = change.replyLimitCap ?: replyLimitCap,
        repeatLimit = change.repeatLimit ?: repeatLimit,
        concurrency = change.concurrency ?: concurrency,
        timeoutMs = change.timeoutMs ?: timeoutMs,
        retries = change.retries ?: retries,
        backoffMs = change.backoffMs ?: backoffMs,
        trace = change.trace ?: trace,
    )
}

data class SettingsChange(
    val maxTurns: Int?,
    val replyLimit: Int?,
    val replyLimitCap: Int?,
    val repeatLimit: Int?,
    val concurrency: Int?,
    val timeoutMs: Int?,
    val retries: Int?,
    val backoffMs: Int?,
    val trace: Boolean?,
)

val MAX_TURNS_RANGE = 1..1_000
val REPLY_LIMIT_RANGE = 1..1_000_000
val REPEAT_LIMIT_RANGE = 0..1_000
val CONCURRENCY_RANGE = 1..1_000_000
val DURATION_MS_RANGE = 0..1_000_000_000
val RETRIES_RANGE = 0..10
