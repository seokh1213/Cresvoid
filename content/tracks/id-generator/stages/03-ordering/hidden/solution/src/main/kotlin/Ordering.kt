data class Record(val id: Long, val actual: Long)

private class IdStream(val machine: Long, val offset: Long) {
    val records = mutableListOf<Record>()
}

private data class Inversion(val width: Long, val earlier: Long, val later: Long) {
    fun render(k: Long): String =
        if (width <= k) "ORDERED $width" else "VIOLATION $width $earlier $later"
}

class OrderSession(private val layout: Layout, private val bound: Long) {
    private val streams = mutableMapOf<String, IdStream>()
    private val machines = mutableSetOf<Long>()

    fun execute(tokens: List<String>): String = when (tokens[0]) {
        "STREAM" -> register(tokens)
        "APPEND" -> append(tokens)
        "MERGE" -> {
            val records = sortedRecords()
            if (records.isEmpty()) "EMPTY" else records.joinToString(" ", "IDS ") { it.id.toString() }
        }
        "AUDIT" -> {
            val k = tokens[1].toLong()
            if (k < 0) "ERR K" else largestInversion(sortedRecords()).render(k)
        }
        else -> error("Unknown ordering command")
    }

    private fun register(tokens: List<String>): String {
        val name = tokens[1]
        val machine = tokens[2].toLong()
        val offset = tokens[3].toLong()
        if (!layout.containsMachine(machine)) return "ERR MACHINE"
        if (offset !in -bound..bound) return "ERR OFFSET"
        if (name in streams || machine in machines) return "ERR EXISTS"
        streams[name] = IdStream(machine, offset)
        machines.add(machine)
        return "OK"
    }

    private fun append(tokens: List<String>): String {
        val stream = streams[tokens[1]] ?: return "ERR STREAM"
        val actual = tokens[2].toLong()
        val id = tokens[3].toLong()
        if (actual < 0) return "ERR TIME"
        if (!layout.containsId(id)) return "ERR ID"
        val parts = layout.decode(id)
        if (parts.machine != stream.machine) return "ERR MACHINE"
        if (parts.time - actual != stream.offset) return "ERR OFFSET"
        val previous = stream.records.lastOrNull()
        if (previous != null && (id <= previous.id || actual < previous.actual)) return "ERR ORDER"
        stream.records.add(Record(id, actual))
        return "OK"
    }

    private fun sortedRecords(): List<Record> = streams.values.flatMap { it.records }.sortedBy { it.id }

    private fun largestInversion(records: List<Record>): Inversion {
        var prefixMaximum: Record? = null
        var best = Inversion(0, 0, 0)
        for (record in records) {
            val earlier = prefixMaximum
            if (earlier != null) {
                val width = earlier.actual - record.actual
                if (width > best.width || (width == best.width && earlier.id < best.earlier)) {
                    best = Inversion(width, earlier.id, record.id)
                }
            }
            // Equal maxima keep the smaller ID, which wins witness ties.
            if (earlier == null || record.actual > earlier.actual) prefixMaximum = record
        }
        return best
    }
}

class Ordering {
    private var session: OrderSession? = null

    fun execute(tokens: List<String>, settings: Settings?): String {
        if (tokens[0] != "ORDER_BEGIN") return session?.execute(tokens) ?: "ERR SESSION"
        val current = settings ?: return "ERR UNCONFIGURED"
        val bound = tokens[1].toLong()
        if (bound !in 0..1_000_000_000_000L) return "ERR BOUND"
        session = OrderSession(current.layout, bound)
        return "OK"
    }
}
