class RagApplication {
    private val store = DocumentStore()

    fun execute(command: Command): List<String> = when (command) {
        is Command.PutDocument -> { store.put(command.document); listOf("OK") }
        is Command.DeleteDocument -> { store.delete(command.id); listOf("OK") }
        is Command.Configure -> { store.configure(command.config); listOf("OK") }
        Command.Build -> { val chunks = store.build(); listOf("OK\t${chunks.size}") }
        Command.ListChunks -> store.chunks().map { "${it.id}\t${it.span.start}\t${it.span.end}\t${it.text}" }
    }
}
