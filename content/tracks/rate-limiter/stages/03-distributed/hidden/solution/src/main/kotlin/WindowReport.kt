import kotlin.math.abs

class WindowReport {
    var requests = 0L
        private set
    var logAllowed = 0L
        private set
    val fixed = MethodComparison()
    val sliding = MethodComparison()
    var peakStored = StoredCounts(log = 0, fixed = 0, sliding = 0)
        private set

    fun add(verdict: WindowVerdict, stored: StoredCounts) {
        requests++
        if (verdict.log.allowed) logAllowed++
        fixed.add(verdict.fixed, exact = verdict.log)
        sliding.add(verdict.sliding, exact = verdict.log)
        peakStored = peakStored.peakWith(stored)
    }
}

class MethodComparison {
    var allowed = 0L
        private set
    var wrongAllow = 0L
        private set
    var wrongDeny = 0L
        private set
    var maxError = 0L
        private set

    fun add(judged: MethodVerdict, exact: MethodVerdict) {
        if (judged.allowed) allowed++
        if (judged.allowed && !exact.allowed) wrongAllow++
        if (!judged.allowed && exact.allowed) wrongDeny++
        maxError = maxOf(maxError, abs(judged.count - exact.count))
    }
}
