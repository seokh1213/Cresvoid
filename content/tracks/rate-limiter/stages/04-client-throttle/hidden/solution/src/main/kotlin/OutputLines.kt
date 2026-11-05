fun verdictLine(verdict: Verdict): String =
    when (verdict) {
        is TokenVerdict.Allow -> "ALLOW ${tokenText(verdict.remainingMilliTokens)}"
        is TokenVerdict.Deny -> "DENY ${tokenText(verdict.milliTokens)} ${verdict.retryAt}"
        is LeakyVerdict.Allow -> "ALLOW ${verdict.releaseAt} ${verdict.freeSlots}"
        is LeakyVerdict.Deny -> "DENY ${verdict.slotOpensAt}"
        is WindowVerdict ->
            "fixed=${methodText(verdict.fixed)} log=${methodText(verdict.log)} sliding=${methodText(verdict.sliding)}"
    }

fun reportLines(report: WindowReport): List<String> =
    listOf(
        "log requests=${report.requests} allowed=${report.logAllowed}",
        comparisonLine("fixed", report.fixed),
        comparisonLine("sliding", report.sliding),
        storedLine(report.peakStored),
    )

fun eventLine(time: Long, requestNumber: Long, result: RequestResult): String = "$time #$requestNumber ${resultText(result)}"

private fun tokenText(milliTokens: Long): String {
    val whole = milliTokens / MILLI_TOKENS_PER_TOKEN
    val fraction = (milliTokens % MILLI_TOKENS_PER_TOKEN).toString().padStart(3, '0')
    return "$whole.$fraction"
}

private fun methodText(verdict: MethodVerdict): String = "${if (verdict.allowed) "ALLOW" else "DENY"}:${verdict.count}"

private fun comparisonLine(method: String, comparison: MethodComparison): String =
    "$method allowed=${comparison.allowed} wrong_allow=${comparison.wrongAllow} " +
        "wrong_deny=${comparison.wrongDeny} max_error=${comparison.maxError}"

private fun storedLine(stored: StoredCounts): String =
    "stored log=${stored.log} fixed=${stored.fixed} sliding=${stored.sliding}"

private fun resultText(result: RequestResult): String =
    when (result) {
        is RequestResult.Allow -> "ALLOW ${result.count}"
        is RequestResult.Deny -> "DENY ${result.count}"
        RequestResult.Open -> "OPEN"
        RequestResult.Shed -> "SHED"
    }
