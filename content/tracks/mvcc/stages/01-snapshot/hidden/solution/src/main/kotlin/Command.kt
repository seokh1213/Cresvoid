enum class IsolationLevel {
    READ_COMMITTED,
    REPEATABLE_READ,
}

data class KeyRange(val start: String, val end: String)

sealed interface Command {
    val transaction: String

    data class Begin(override val transaction: String, val isolation: IsolationLevel) : Command

    sealed interface Statement : Command

    data class Read(override val transaction: String, val key: String) : Statement

    data class Write(override val transaction: String, val key: String, val value: String) : Statement

    data class Scan(override val transaction: String, val range: KeyRange) : Statement

    data class Commit(override val transaction: String) : Statement

    data class Rollback(override val transaction: String) : Statement
}
