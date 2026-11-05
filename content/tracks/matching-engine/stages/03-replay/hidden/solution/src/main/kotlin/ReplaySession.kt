import java.io.BufferedReader

class ReplaySession(private val input: BufferedReader, private val output: (String) -> Unit) {
    private var engine = MatchingEngine(output)
    private var lastSequence = 0L
    private var baseSequence = 0L
    private val journal = ArrayList<String>()

    fun accept(tokens: List<String>) {
        if (tokens[0] in CommandParser.mutationNames) {
            record(tokens)
            engine.apply(CommandParser.parse(tokens) as Command.Mutation)
            return
        }
        when (val command = CommandParser.parse(tokens)) {
            is Command.Event -> replay(command)
            is Command.Restore -> restore(command.header)
            Command.Book -> output(engine.book.render())
            Command.Snapshot -> SnapshotCodec.write(engine, lastSequence, output)
            Command.Journal -> writeJournal()
            is Command.Mutation -> error("Mutations are recorded before parsing")
        }
    }

    private fun replay(event: Command.Event) {
        if (event.sequence != lastSequence + 1) throw Rejected("SEQUENCE")
        record(event.body)
        engine.apply(CommandParser.parse(event.body) as Command.Mutation)
    }

    private fun record(tokens: List<String>) {
        journal.add(tokens.joinToString(" "))
        lastSequence++
    }

    private fun restore(header: SnapshotHeader) {
        engine = SnapshotCodec.read(header, input, output)
        lastSequence = header.lastSequence
        baseSequence = lastSequence
        journal.clear()
        output("RESTORED $lastSequence")
    }

    private fun writeJournal() {
        output("JOURNAL $baseSequence ${journal.size}")
        journal.forEachIndexed { index, body -> output("EVENT ${baseSequence + index + 1} $body") }
    }
}
