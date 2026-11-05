class Replica {
    private val latest = mutableMapOf<String, VersionedMutation>()
    private val received = mutableSetOf<Long>()
    private var checkpoint = -1L
    private var maximum = -1L

    fun replay(record: VersionedMutation, currentTerm: Long): String {
        val version = record.version
        if (version.term < currentTerm) return "STALE_TERM"
        if (version.term > currentTerm) return "ERR TERM"
        if (!received.add(version.sequence)) return "DUPLICATE"
        maximum = maxOf(maximum, version.sequence)
        while (checkpoint + 1 in received) checkpoint++

        val previous = latest[record.mutation.id]
        if (previous != null && previous.version.sequence > version.sequence) return "IGNORED"
        latest[record.mutation.id] = record
        return "APPLIED"
    }

    fun get(id: String): String = latest[id].describe()

    fun state(): String = "checkpoint=$checkpoint max=$maximum"

    fun clear() {
        latest.clear()
        received.clear()
        checkpoint = -1
        maximum = -1
    }
}
