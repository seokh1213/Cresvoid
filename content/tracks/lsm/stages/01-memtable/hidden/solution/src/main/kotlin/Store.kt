import java.util.TreeMap

class Store(private val capacity: Int) {
    private val memory = TreeMap<String, Record>()
    private val files = TreeMap<Long, SortedTable>()
    private var sequence = 0L
    private var nextFile = 1L

    fun write(key: String, payload: Payload) {
        memory[key] = Record(key, ++sequence, payload)
        if (memory.size == capacity) flush()
    }

    fun flush() {
        if (memory.isEmpty()) return
        val table = SortedTable(nextFile++, 0, memory.values.toList())
        files[table.number] = table
        memory.clear()
    }

    fun get(key: String): Record? {
        memory[key]?.let { return it }
        for (table in files.descendingMap().values) {
            table.find(key)?.let { return it }
        }
        return null
    }

    fun layout(): List<String> = buildList {
        add("MEM ${memory.size}")
        files.values.forEach { add(it.layout()) }
        add("END")
    }
}
