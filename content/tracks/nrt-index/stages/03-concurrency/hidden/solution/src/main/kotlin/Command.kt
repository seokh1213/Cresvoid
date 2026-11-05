sealed interface Mutation {
    val id: String
    data class Put(override val id: String, val words: List<String>) : Mutation
    data class Delete(override val id: String) : Mutation
}

enum class DurabilityMode { REQUEST, ASYNC }

sealed interface Command {
    data class Write(val mutation: Mutation) : Command
    data class Get(val id: String) : Command
    data class Search(val word: String) : Command
    data class Tick(val delta: Long) : Command
    data object Refresh : Command
    data object Merge : Command
    data object Segments : Command
    data object State : Command
    data class Durability(val mode: DurabilityMode) : Command
    data object Sync : Command
    data object Flush : Command
    data object Crash : Command
    data object Log : Command
    data class VersionOf(val id: String) : Command
    data class CompareAndSet(val put: Mutation.Put, val expected: Version) : Command
    data object Promote : Command
    data class Replay(val record: VersionedMutation) : Command
    data class ReplicaGet(val id: String) : Command
    data object ReplicaState : Command
}

class CommandParser {
    private val idPattern = Regex("[a-z][a-z0-9_]{0,15}")
    private val wordPattern = Regex("[a-z]{1,20}")
    private val numberPattern = Regex("0|[1-9][0-9]*")

    fun parse(line: String): Command? {
        val tokens = line.split(Regex("[ \\t]+"))
        return when (tokens.first()) {
            "PUT" -> parseMutation(tokens)?.let(Command::Write)
            "DEL" -> parseMutation(tokens)?.let(Command::Write)
            "GET" -> tokens.getOrNull(1)?.takeIf { tokens.size == 2 && validId(it) }?.let(Command::Get)
            "SEARCH" -> tokens.getOrNull(1)
                ?.takeIf { tokens.size == 2 && (it == "*" || wordPattern.matches(it)) }
                ?.let(Command::Search)
            "TICK" -> tokens.getOrNull(1)?.takeIf { tokens.size == 2 }
                ?.let { number(it, 0, 60_000) }?.let(Command::Tick)
            "REFRESH" -> Command.Refresh.takeIf { tokens.size == 1 }
            "MERGE" -> Command.Merge.takeIf { tokens.size == 1 }
            "SEGMENTS" -> Command.Segments.takeIf { tokens.size == 1 }
            "STATE" -> Command.State.takeIf { tokens.size == 1 }
            "DURABILITY" -> tokens.getOrNull(1)?.takeIf { tokens.size == 2 }?.let { mode ->
                when (mode) {
                    "request" -> Command.Durability(DurabilityMode.REQUEST)
                    "async" -> Command.Durability(DurabilityMode.ASYNC)
                    else -> null
                }
            }
            "SYNC" -> Command.Sync.takeIf { tokens.size == 1 }
            "FLUSH" -> Command.Flush.takeIf { tokens.size == 1 }
            "CRASH" -> Command.Crash.takeIf { tokens.size == 1 }
            "LOG" -> Command.Log.takeIf { tokens.size == 1 }
            "VERSION" -> tokens.getOrNull(1)?.takeIf { tokens.size == 2 && validId(it) }
                ?.let(Command::VersionOf)
            "CAS" -> parseCompareAndSet(tokens)
            "PROMOTE" -> Command.Promote.takeIf { tokens.size == 1 }
            "REPLAY" -> parseReplay(tokens)
            "RGET" -> tokens.getOrNull(1)?.takeIf { tokens.size == 2 && validId(it) }
                ?.let(Command::ReplicaGet)
            "RSTATE" -> Command.ReplicaState.takeIf { tokens.size == 1 }
            else -> null
        }
    }

    private fun parseMutation(tokens: List<String>): Mutation? {
        val id = tokens.getOrNull(1)?.takeIf(::validId) ?: return null
        return when (tokens.first()) {
            "PUT" -> tokens.drop(2).takeIf(::validBody)?.let { Mutation.Put(id, it) }
            "DEL" -> Mutation.Delete(id).takeIf { tokens.size == 2 }
            else -> null
        }
    }

    private fun validId(id: String): Boolean = idPattern.matches(id)

    private fun parseCompareAndSet(tokens: List<String>): Command? {
        val id = tokens.getOrNull(1)?.takeIf(::validId) ?: return null
        val expected = parseVersion(tokens.drop(2)) ?: return null
        val words = tokens.drop(4).takeIf(::validBody) ?: return null
        return Command.CompareAndSet(Mutation.Put(id, words), expected)
    }

    private fun parseReplay(tokens: List<String>): Command? {
        val version = parseVersion(tokens.drop(1)) ?: return null
        val mutation = parseMutation(tokens.drop(3)) ?: return null
        return Command.Replay(VersionedMutation(mutation, version))
    }

    private fun parseVersion(tokens: List<String>): Version? {
        val sequence = tokens.getOrNull(0)?.let { number(it, 0, 1_000_000_000) } ?: return null
        val term = tokens.getOrNull(1)?.let { number(it, 1, 1_000_000_000) } ?: return null
        return Version(sequence, term)
    }

    private fun validBody(words: List<String>): Boolean =
        words.size in 1..8 && words.all(wordPattern::matches)

    private fun number(token: String, minimum: Long, maximum: Long): Long? =
        token.takeIf(numberPattern::matches)?.toLongOrNull()?.takeIf { it in minimum..maximum }
}
