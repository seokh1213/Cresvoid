enum class IsolationLevel {
    READ_COMMITTED,
    REPEATABLE_READ,
}

data class KeyRange(val start: String, val end: String) {
    operator fun contains(key: String): Boolean = key >= start && key < end

    fun overlaps(other: KeyRange): Boolean = start < other.end && other.start < end
}

sealed interface Command {
    val transaction: String

    data class Begin(override val transaction: String, val isolation: IsolationLevel) : Command

    sealed interface Statement : Command

    data class Read(override val transaction: String, val key: String, val lock: LockMode?) : Statement

    data class Write(override val transaction: String, val key: String, val value: String) : Statement

    data class Scan(override val transaction: String, val range: KeyRange, val lock: LockMode?) : Statement

    data class Commit(override val transaction: String) : Statement

    data class Rollback(override val transaction: String) : Statement
}
