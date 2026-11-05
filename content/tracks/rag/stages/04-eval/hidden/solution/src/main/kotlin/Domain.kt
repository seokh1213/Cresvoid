import java.util.TreeMap

data class Document(val id: Int, val title: String, val body: String)
enum class ChunkMode { CHAR, SENTENCE }
enum class ContextMode { NONE, TITLE }
data class ChunkConfig(
    val size: Int = 200,
    val overlap: Int = 20,
    val mode: ChunkMode = ChunkMode.SENTENCE,
    val context: ContextMode = ContextMode.TITLE,
)
data class ChunkId(val document: Int, val ordinal: Int) : Comparable<ChunkId> {
    override fun compareTo(other: ChunkId): Int =
        compareValuesBy(this, other, ChunkId::document, ChunkId::ordinal)
    override fun toString(): String = "$document:$ordinal"
}
data class Span(val start: Int, val end: Int)
data class Chunk(val id: ChunkId, val span: Span, val text: String)

sealed interface BuildState {
    data object Pending : BuildState
    class Ready(val chunks: List<Chunk>) : BuildState
}

class DocumentStore {
    private val documents = TreeMap<Int, Document>()
    var config = ChunkConfig()
        private set
    var state: BuildState = BuildState.Pending
        private set

    fun put(document: Document) {
        documents[document.id] = document
        state = BuildState.Pending
    }

    fun delete(id: Int) {
        if (id !in documents) fail("NOT_FOUND")
        documents.remove(id)
        state = BuildState.Pending
    }

    fun configure(next: ChunkConfig) {
        config = next
        state = BuildState.Pending
    }

    fun build(): List<Chunk> = preview(config).also { state = BuildState.Ready(it) }
    fun preview(settings: ChunkConfig): List<Chunk> =
        documents.values.flatMap { Chunker(settings).split(it) }

    fun chunks(): List<Chunk> = when (val current = state) {
        BuildState.Pending -> fail("NOT_BUILT")
        is BuildState.Ready -> current.chunks
    }
}

class CommandFailure(val reason: String) : RuntimeException(null, null, false, false)
fun fail(reason: String): Nothing = throw CommandFailure(reason)
fun normalize(text: String): String = text.replace(Regex(" +"), " ").trim(' ')
