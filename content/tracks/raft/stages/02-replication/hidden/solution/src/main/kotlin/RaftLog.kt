class RaftLog {
    private val entries = ArrayList<LogEntry>()

    val lastIndex: Int get() = entries.size

    val all: List<LogEntry> get() = entries

    fun termAt(index: Int): Int = if (index == 0) 0 else entries[index - 1].term

    fun entriesFrom(index: Int, limit: Int): List<LogEntry> =
        entries.subList(index - 1, minOf(entries.size, index - 1 + limit)).toList()

    fun append(entry: LogEntry): Int {
        entries += entry
        return lastIndex
    }

    fun hasEntry(index: Int, term: Int): Boolean = index == 0 || (index <= lastIndex && termAt(index) == term)

    fun storeEntriesAfter(prevLogIndex: Int, incoming: List<LogEntry>) {
        for ((offset, entry) in incoming.withIndex()) {
            val index = prevLogIndex + 1 + offset
            if (index > lastIndex) {
                entries += entry
            } else if (termAt(index) != entry.term) {
                entries.subList(index - 1, entries.size).clear()
                entries += entry
            }
        }
    }
}
