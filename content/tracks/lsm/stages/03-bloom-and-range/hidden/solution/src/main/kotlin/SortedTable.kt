import java.util.TreeMap

class SortedTable(val number: Long, val level: Int, input: List<Record>) {
    private val records = TreeMap<String, Record>().apply {
        input.forEach { record -> put(record.key, record) }
    }
    private val filter = BloomFilter(records.keys)
    val size: Int get() = records.size
    val minimum: String = records.firstKey()
    val maximum: String = records.lastKey()

    fun find(key: String): Record? = records[key]

    fun mayContain(key: String): Boolean = filter.mayContain(key)

    fun rangeFrom(lower: String): Iterator<Record> = records.tailMap(lower, true).values.iterator()

    fun entries(): Collection<Record> = records.values.toList()

    fun layout(): String = "FILE $number $level $size $minimum $maximum"
}
