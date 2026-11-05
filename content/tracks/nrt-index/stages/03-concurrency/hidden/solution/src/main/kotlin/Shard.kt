data class ShardCommit(val index: IndexCommit, val versions: Map<String, VersionedMutation>)

class Shard {
    private val index = SearchIndex()
    private val translog = Translog()
    private val replica = Replica()
    private val versions = mutableMapOf<String, VersionedMutation>()
    private var commit = ShardCommit(index.commit(), emptyMap())
    private var currentTerm = 1L
    private var nextSequence = 0L
    private var time = 0L
    private var lastSearch: Long? = null

    fun execute(command: Command): String = when (command) {
        is Command.Write -> {
            write(command.mutation)
            "OK"
        }
        is Command.Get -> index.get(command.id)
        is Command.Search -> search(command.word)
        is Command.Tick -> {
            tick(command.delta)
            "OK"
        }
        Command.Refresh -> {
            index.refresh()
            "OK"
        }
        Command.Merge -> {
            index.merge()
            "OK"
        }
        Command.Segments -> index.describeSegments()
        Command.State -> index.state(time)
        is Command.Durability -> {
            translog.mode = command.mode
            "OK"
        }
        Command.Sync -> {
            translog.sync()
            "OK"
        }
        Command.Flush -> {
            flush()
            "OK"
        }
        Command.Crash -> {
            crash()
            "OK"
        }
        Command.Log -> translog.describe()
        is Command.VersionOf -> versions[command.id].describe()
        is Command.CompareAndSet -> compareAndSet(command)
        Command.Promote -> {
            currentTerm++
            "TERM $currentTerm"
        }
        is Command.Replay -> replica.replay(command.record, currentTerm)
        is Command.ReplicaGet -> replica.get(command.id)
        Command.ReplicaState -> replica.state()
    }

    private fun write(mutation: Mutation): Version {
        val version = Version(nextSequence++, currentTerm)
        val record = VersionedMutation(mutation, version)
        applyPrimary(record)
        translog.append(record)
        return version
    }

    private fun applyPrimary(record: VersionedMutation) {
        versions[record.mutation.id] = record
        index.write(record.mutation)
    }

    private fun compareAndSet(command: Command.CompareAndSet): String {
        val previous = versions[command.put.id]
        if (previous?.mutation !is Mutation.Put || previous.version != command.expected) return "CONFLICT"
        val assigned = write(command.put)
        return "OK ${assigned.sequence} ${assigned.term}"
    }

    private fun search(word: String): String {
        val previousSearch = lastSearch
        if (previousSearch == null || time - previousSearch >= 30_000) index.refresh()
        val result = index.search(word)
        lastSearch = time
        return result
    }

    private fun tick(delta: Long) {
        val destination = time + delta
        var boundary = (time / 1_000 + 1) * 1_000
        while (boundary <= destination) {
            if (boundary % 5_000 == 0L) translog.sync()
            val previousSearch = lastSearch
            if (previousSearch != null && boundary - previousSearch < 30_000) index.refresh()
            boundary += 1_000
        }
        time = destination
    }

    private fun flush() {
        index.refresh()
        commit = ShardCommit(index.commit(), versions.toMap())
        translog.newGeneration()
    }

    private fun crash() {
        index.restore(commit.index)
        versions.clear()
        versions.putAll(commit.versions)
        for (record in translog.recover()) applyPrimary(record)
        lastSearch = null
        replica.clear()
    }
}
