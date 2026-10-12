enum class LockMode {
    SHARED,
    EXCLUSIVE,
    ;

    fun conflictsWith(other: LockMode): Boolean = this == EXCLUSIVE || other == EXCLUSIVE
}

sealed interface LockTarget {
    fun overlaps(range: KeyRange): Boolean

    data class Key(val key: String) : LockTarget {
        override fun overlaps(range: KeyRange): Boolean = key in range
    }

    data class Range(val range: KeyRange) : LockTarget {
        override fun overlaps(range: KeyRange): Boolean = this.range.overlaps(range)
    }
}

data class LockRequest(val mode: LockMode, val target: LockTarget)

fun Command.Statement.lockRequest(): LockRequest? =
    when (this) {
        is Command.Write -> LockRequest(LockMode.EXCLUSIVE, LockTarget.Key(key))
        is Command.Read -> lock?.let { LockRequest(it, LockTarget.Key(key)) }
        is Command.Scan -> lock?.let { LockRequest(it, LockTarget.Range(range)) }
        is Command.Commit, is Command.Rollback -> null
    }
