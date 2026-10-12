sealed interface Verdict

sealed interface TokenVerdict : Verdict {
    data class Allow(val remainingMilliTokens: Long) : TokenVerdict

    data class Deny(val milliTokens: Long, val retryAt: Long) : TokenVerdict
}

sealed interface LeakyVerdict : Verdict {
    data class Allow(val releaseAt: Long, val freeSlots: Long) : LeakyVerdict

    data class Deny(val slotOpensAt: Long) : LeakyVerdict
}
