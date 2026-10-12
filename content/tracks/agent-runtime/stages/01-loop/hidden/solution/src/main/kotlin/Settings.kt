data class Settings(
    val maxTurns: Int = 10,
    val replyLimit: Int = 1_000,
    val replyLimitCap: Int = 4_000,
) {
    val isConsistent: Boolean get() = replyLimit <= replyLimitCap

    fun with(change: SettingsChange): Settings = Settings(
        maxTurns = change.maxTurns ?: maxTurns,
        replyLimit = change.replyLimit ?: replyLimit,
        replyLimitCap = change.replyLimitCap ?: replyLimitCap,
    )
}

data class SettingsChange(
    val maxTurns: Int?,
    val replyLimit: Int?,
    val replyLimitCap: Int?,
)

val MAX_TURNS_RANGE = 1..1_000
val REPLY_LIMIT_RANGE = 1..1_000_000
