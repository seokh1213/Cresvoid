class Chunker(private val config: ChunkConfig) {
    fun split(document: Document): List<Chunk> {
        val points = document.body.codePoints().toArray()
        if (points.isEmpty()) return emptyList()
        val spans = when (config.mode) {
            ChunkMode.CHAR -> characterSpans(points.size)
            ChunkMode.SENTENCE -> sentenceSpans(points)
        }
        val prefix = if (config.context == ContextMode.TITLE && document.title.isNotEmpty()) {
            document.title + " | "
        } else ""
        return spans.mapIndexed { ordinal, span ->
            Chunk(ChunkId(document.id, ordinal), span,
                prefix + String(points, span.start, span.end - span.start))
        }
    }

    private fun characterSpans(length: Int): List<Span> = buildList {
        var start = 0
        while (start < length) {
            val end = minOf(start + config.size, length)
            add(Span(start, end))
            if (end == length) break
            start = end - config.overlap
        }
    }

    private fun sentenceSpans(points: IntArray): List<Span> {
        val fragments = sentenceFragments(points)
        return buildList {
            var first = 0
            while (first < fragments.size) {
                val limit = fragments[first].start + config.size
                val afterLast = upperBound(fragments.size) { fragments[it].end <= limit }
                val last = afterLast - 1
                add(Span(fragments[first].start, fragments[last].end))
                if (afterLast == fragments.size) break
                val earliest = maxOf(fragments[last].end - config.overlap,
                    fragments[afterLast].end - config.size)
                val next = upperBound(fragments.size) { fragments[it].start < earliest }
                first = maxOf(first + 1, next).coerceAtMost(afterLast)
            }
        }
    }

    private fun sentenceFragments(points: IntArray): List<Span> = buildList {
        var start = 0
        for (index in points.indices) {
            val endsSentence = points[index] in intArrayOf(46, 63, 33) &&
                (index + 1 == points.size || points[index + 1] == 32)
            if (endsSentence) {
                addFragments(start, index + 1)
                start = minOf(index + 2, points.size)
            }
        }
        if (start < points.size) addFragments(start, points.size)
    }

    private fun MutableList<Span>.addFragments(start: Int, end: Int) {
        var position = start
        while (position < end) {
            val next = minOf(position + config.size, end)
            add(Span(position, next))
            position = next
        }
    }

    private fun upperBound(length: Int, belongsLeft: (Int) -> Boolean): Int {
        var low = 0
        var high = length
        while (low < high) {
            val middle = (low + high) ushr 1
            if (belongsLeft(middle)) low = middle + 1 else high = middle
        }
        return low
    }
}
