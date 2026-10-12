fun verdictLine(verdict: Verdict): String =
    when (verdict) {
        is TokenVerdict.Allow -> "ALLOW ${tokenText(verdict.remainingMilliTokens)}"
        is TokenVerdict.Deny -> "DENY ${tokenText(verdict.milliTokens)} ${verdict.retryAt}"
        is LeakyVerdict.Allow -> "ALLOW ${verdict.releaseAt} ${verdict.freeSlots}"
        is LeakyVerdict.Deny -> "DENY ${verdict.slotOpensAt}"
    }

private fun tokenText(milliTokens: Long): String {
    val whole = milliTokens / MILLI_TOKENS_PER_TOKEN
    val fraction = (milliTokens % MILLI_TOKENS_PER_TOKEN).toString().padStart(3, '0')
    return "$whole.$fraction"
}
