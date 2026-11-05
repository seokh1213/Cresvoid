import java.util.TreeMap

class SortedTable(val number: Long, val level: Int, input: List<Record>) {
    private val records = TreeMap<String, Record>().apply {
        input.forEach { record -> put(record.key, record) }
    }
    val size: Int get() = records.size
    val minimum: String = records.firstKey()
    val maximum: String = records.lastKey()

    fun find(key: String): Record? = records[key]

    fun layout(): String = "FILE $number $level $size $minimum $maximum"
}
