data class Settings(
    val maxTurns: Int = 10,
    val replyLimit: Int = 1_000,
    val replyLimitCap: Int = 4_000,
    val repeatLimit: Int = 0,
) {
    val isConsistent: Boolean get() = replyLimit <= replyLimitCap

    fun with(change: SettingsChange): Settings = Settings(
        maxTurns = change.maxTurns ?: maxTurns,
        replyLimit = change.replyLimit ?: replyLimit,
        replyLimitCap = change.replyLimitCap ?: replyLimitCap,
        repeatLimit = change.repeatLimit ?: repeatLimit,
    )
}

data class SettingsChange(
    val maxTurns: Int?,
    val replyLimit: Int?,
    val replyLimitCap: Int?,
    val repeatLimit: Int?,
)

val MAX_TURNS_RANGE = 1..1_000
val REPLY_LIMIT_RANGE = 1..1_000_000
val REPEAT_LIMIT_RANGE = 0..1_000
