class LocalCopy(val gateway: Gateway, val counter: CounterKey) {
    private var storeValue = 0L
    private var sentShare = 0L
    private var unsentShare = 0L
    private var awaitingReply = false
    private var lastSyncTime: Long? = null

    fun countWithNewRequest(): Long = storeValue + sentShare + unsentShare + 1

    fun addAllowed() {
        unsentShare++
    }

    fun canSync(time: Long, syncPeriod: Long): Boolean {
        val lastSync = lastSyncTime
        return !awaitingReply && (lastSync == null || time >= lastSync + syncPeriod)
    }

    fun startSync(time: Long): Long {
        sentShare = unsentShare
        unsentShare = 0
        awaitingReply = true
        lastSyncTime = time
        return sentShare
    }

    fun acceptStoreTotal(total: Long) {
        storeValue = total
        sentShare = 0
        awaitingReply = false
    }

    fun takeBackSentShare() {
        unsentShare += sentShare
        sentShare = 0
        awaitingReply = false
    }
}
