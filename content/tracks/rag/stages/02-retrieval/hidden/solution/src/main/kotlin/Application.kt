class RagApplication {
    private val store = DocumentStore()
    private val embeddings = Embeddings()
    private var index: LexicalIndex? = null
    private var chunkIds: Set<ChunkId>? = null

    private fun invalidate() {
        embeddings.clear()
        index = null
        chunkIds = null
    }

    private fun lexical(): LexicalIndex {
        val chunks = store.chunks()
        return index ?: LexicalIndex(chunks).also { index = it }
    }

    private fun hits(ranking: List<SearchHit>, places: Int): List<String> =
        ranking.map { "${it.chunk.id}\t${fixedUnits(it.units, places)}" }

    fun execute(command: Command): List<String> = when (command) {
        is Command.PutDocument -> { store.put(command.document); invalidate(); listOf("OK") }
        is Command.DeleteDocument -> { store.delete(command.id); invalidate(); listOf("OK") }
        is Command.Configure -> { store.configure(command.config); invalidate(); listOf("OK") }
        Command.Build -> { val chunks = store.build(); invalidate(); listOf("OK\t${chunks.size}") }
        Command.ListChunks -> store.chunks().map { "${it.id}\t${it.span.start}\t${it.span.end}\t${it.text}" }
        is Command.PutVector -> {
            val chunks = store.chunks()
            if (chunkIds == null) chunkIds = chunks.mapTo(HashSet()) { it.id }
            if (command.id !in chunkIds.orEmpty()) fail("NOT_FOUND")
            embeddings.put(command.id, command.vector)
            listOf("OK")
        }
        is Command.LexicalSearch -> hits(lexical().search(command.text).take(command.k), 4)
        is Command.VectorSearch -> {
            val chunks = store.chunks()
            embeddings.checkDimension(command.vector)
            hits(embeddings.search(chunks, command.vector).take(command.k), 6)
        }
    }
}
