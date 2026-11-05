data class DocumentRow(val id: String, val words: List<String>, var deleted: Boolean = false)
data class Segment(val number: Int, val rows: List<DocumentRow>)

data class IndexCommit(
    val latest: Map<String, List<String>>,
    val segments: List<Segment>,
    val nextSegmentNumber: Int,
)

class SearchIndex {
    private val latest = mutableMapOf<String, List<String>>()
    private val buffer = mutableListOf<Mutation>()
    private val segments = mutableListOf<Segment>()
    private val visible = mutableMapOf<String, DocumentRow>()
    private var nextSegmentNumber = 1

    fun write(mutation: Mutation) {
        when (mutation) {
            is Mutation.Put -> latest[mutation.id] = mutation.words
            is Mutation.Delete -> latest.remove(mutation.id)
        }
        buffer.add(mutation)
    }

    fun get(id: String): String = latest[id]?.joinToString(" ") ?: "MISSING"

    fun refresh() {
        val newRows = mutableListOf<DocumentRow>()
        for (mutation in buffer) {
            visible.remove(mutation.id)?.deleted = true
            if (mutation is Mutation.Put) {
                val row = DocumentRow(mutation.id, mutation.words)
                newRows.add(row)
                visible[mutation.id] = row
            }
        }
        if (newRows.isNotEmpty()) segments.add(Segment(nextSegmentNumber++, newRows))
        buffer.clear()
    }

    fun search(word: String): String = visible.values.asSequence()
        .filter { word == "*" || word in it.words }
        .map { it.id }.sorted().joinToString(" ").ifEmpty { "NONE" }

    fun merge() {
        if (segments.isEmpty()) return
        if (segments.size == 1 && segments.single().rows.none { it.deleted }) return
        val liveRows = visible.values.toList()
        segments.clear()
        if (liveRows.isNotEmpty()) segments.add(Segment(nextSegmentNumber++, liveRows))
    }

    fun describeSegments(): String = segments.joinToString(" ") { segment ->
        val deleted = segment.rows.count { it.deleted }
        "${segment.number}:${segment.rows.size - deleted}:$deleted"
    }.ifEmpty { "NONE" }

    fun state(time: Long): String = "time=$time buffer=${buffer.size} visible=${visible.size}"

    fun commit(): IndexCommit = IndexCommit(latest.toMap(), copySegments(segments), nextSegmentNumber)

    fun restore(commit: IndexCommit) {
        latest.clear()
        latest.putAll(commit.latest)
        buffer.clear()
        segments.clear()
        segments.addAll(copySegments(commit.segments))
        nextSegmentNumber = commit.nextSegmentNumber
        visible.clear()
        for (segment in segments) {
            for (row in segment.rows) {
                if (!row.deleted) visible[row.id] = row
            }
        }
    }

    private fun copySegments(source: List<Segment>): List<Segment> = source.map { segment ->
        Segment(segment.number, segment.rows.map { it.copy() })
    }
}
