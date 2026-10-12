class LeakyBucket(private val queueLength: Long, private val leakInterval: Long) : Limiter {
    private val lastReleases = HashMap<String, Long>()

    override fun request(key: String, time: Long): LeakyVerdict {
        val lastRelease = lastReleases[key] ?: return admit(key, release = time, time)
        val waiting = waitingCount(lastRelease, time)
        if (waiting >= queueLength) {
            return LeakyVerdict.Deny(slotOpensAt = lastRelease - (waiting - 1) * leakInterval)
        }
        return admit(key, release = maxOf(time, lastRelease + leakInterval), time)
    }

    private fun admit(key: String, release: Long, time: Long): LeakyVerdict.Allow {
        lastReleases[key] = release
        return LeakyVerdict.Allow(release, freeSlots = queueLength - waitingCount(release, time))
    }

    // 기다리는 요청은 앞 요청보다 정확히 한 간격 뒤에 나가므로 마지막 내보내는 시각만으로 셀 수 있다
    private fun waitingCount(lastRelease: Long, time: Long): Long =
        if (lastRelease <= time) 0 else Math.ceilDiv(lastRelease - time, leakInterval)
}
