import java.util.TreeMap

class Store(private val capacity: Int) {
    private val memory = TreeMap<String, Record>()
    private val levels = List(3, ::TableLevel)
    private val liveKeys = mutableSetOf<String>()
    private val readMetrics = ReadMetrics()
    private var sequence = 0L
    private var nextFile = 1L
    private var fileWrites = 0L

    fun write(key: String, payload: Payload) {
        memory[key] = Record(key, ++sequence, payload)
        when (payload) {
            is Payload.Value -> liveKeys.add(key)
            Payload.Tombstone -> liveKeys.remove(key)
        }
        if (memory.size == capacity) flush()
    }

    fun flush() {
        if (memory.isEmpty()) return
        createFile(0, memory.values.toList())
        memory.clear()
    }

    private fun createFile(level: Int, records: List<Record>) {
        val table = SortedTable(nextFile++, level, records)
        levels[level].add(table)
        fileWrites += records.size
    }

    fun get(key: String): Record? {
        memory[key]?.let { return it }
        for (level in levels) {
            for (table in level.candidates(key)) {
                table.find(key)?.let { return it }
            }
        }
        return null
    }

    fun bloomGet(key: String): Record? {
        memory[key]?.let { return it }
        for (level in levels) {
            for (table in level.candidates(key)) {
                readMetrics.inspect(table, key)?.let { return it }
            }
        }
        return null
    }

    fun reads(): String = readMetrics.output()

    fun range(lower: String, upper: String, limit: Int): List<String> {
        if (lower > upper || limit == 0) return listOf("ROWS 0", "END")
        val sources = buildList {
            add(memory.tailMap(lower, true).values.iterator())
            for (level in levels) {
                level.rangeTables(lower, upper).forEach { add(it.rangeFrom(lower)) }
            }
        }
        val rows = RangeMerge(upper).read(sources, limit)
        return buildList {
            add("ROWS ${rows.size}")
            rows.forEach { record ->
                val value = record.payload as Payload.Value
                add("ROW ${record.key} ${value.text}")
            }
            add("END")
        }
    }

    fun compact() {
        while (true) {
            val sourceLevel = when {
                levels[0].recordCount > 2 * capacity -> 0
                levels[1].recordCount > 4 * capacity -> 1
                else -> return
            }
            mergeOne(sourceLevel)
        }
    }

    private fun mergeOne(sourceLevel: Int) {
        val source = levels[sourceLevel].source()
        val destination = levels[sourceLevel + 1]
        val overlaps = destination.overlapping(source.minimum, source.maximum)
        val merged = newestRecords(listOf(source) + overlaps)
        val retained = if (sourceLevel + 1 == 2) {
            merged.filter { it.payload is Payload.Value }
        } else merged

        levels[sourceLevel].remove(source)
        overlaps.forEach(destination::remove)
        for (chunk in retained.chunked(capacity)) {
            createFile(sourceLevel + 1, chunk)
        }
    }

    private fun newestRecords(tables: List<SortedTable>): List<Record> {
        val newest = TreeMap<String, Record>()
        for (table in tables) {
            for (record in table.entries()) {
                val previous = newest[record.key]
                if (previous == null || previous.sequence < record.sequence) newest[record.key] = record
            }
        }
        return newest.values.toList()
    }

    fun layout(): List<String> = buildList {
        add("MEM ${memory.size}")
        levels.forEach { addAll(it.layout()) }
        add("END")
    }

    fun metrics(): String {
        val physical = memory.size + levels.sumOf { it.recordCount }
        val valid = liveKeys.size
        val writeAmplification = if (sequence == 0L) "NA" else "$fileWrites/$sequence"
        val spaceAmplification = if (valid == 0) "NA" else "$physical/$valid"
        return "METRICS $sequence $fileWrites $physical $valid $writeAmplification $spaceAmplification"
    }
}
