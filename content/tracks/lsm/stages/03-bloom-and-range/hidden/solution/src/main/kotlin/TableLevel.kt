import java.util.TreeMap

class TableLevel(private val number: Int) {
    private val byNumber = TreeMap<Long, SortedTable>()
    private val byMinimum = TreeMap<String, SortedTable>()
    var recordCount: Int = 0
        private set

    fun add(table: SortedTable) {
        byNumber[table.number] = table
        if (number != 0) byMinimum[table.minimum] = table
        recordCount += table.size
    }

    fun remove(table: SortedTable) {
        byNumber.remove(table.number)
        if (number != 0) byMinimum.remove(table.minimum)
        recordCount -= table.size
    }

    fun source(): SortedTable = if (number == 0) {
        byNumber.firstEntry().value
    } else {
        byMinimum.firstEntry().value
    }

    fun candidates(key: String): Collection<SortedTable> {
        if (number == 0) return byNumber.descendingMap().values
        val table = byMinimum.floorEntry(key)?.value ?: return emptyList()
        return if (key <= table.maximum) listOf(table) else emptyList()
    }

    fun overlapping(lower: String, upper: String): List<SortedTable> = buildList {
        val preceding = byMinimum.floorEntry(lower)?.value
        if (preceding != null && preceding.maximum >= lower) add(preceding)
        for (table in byMinimum.tailMap(lower, false).values) {
            if (table.minimum > upper) break
            add(table)
        }
    }

    fun rangeTables(lower: String, upper: String): List<SortedTable> = if (number == 0) {
        byNumber.values.filter { it.minimum <= upper && it.maximum >= lower }
    } else overlapping(lower, upper)

    fun layout(): List<String> = byNumber.values.map { it.layout() }
}
