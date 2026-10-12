data class LogPosition(val lastIndex: Int, val lastTerm: Int) {
    fun isMoreUpToDateThan(other: LogPosition): Boolean =
        lastTerm > other.lastTerm || (lastTerm == other.lastTerm && lastIndex > other.lastIndex)
}
