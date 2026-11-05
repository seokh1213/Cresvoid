import java.util.BitSet

class BloomFilter(keys: Collection<String>) {
    private val bitCount = 8 * keys.size
    private val bits = BitSet(bitCount)

    init {
        keys.forEach { key -> positions(key).forEach(bits::set) }
    }

    fun mayContain(key: String): Boolean = positions(key).all(bits::get)

    private fun positions(key: String): IntArray {
        var first = 0L
        var second = 7L
        for (character in key) {
            first = (31L * first + character.code) % 2147483647L
            second = (131L * second + character.code) % 2147483647L
        }
        return IntArray(3) { index -> ((first + index * second) % bitCount).toInt() }
    }
}

class ReadMetrics {
    private var checks = 0L
    private var negatives = 0L
    private var lookups = 0L
    private var falsePositives = 0L

    fun inspect(table: SortedTable, key: String): Record? {
        if (key < table.minimum || key > table.maximum) return null
        checks++
        if (!table.mayContain(key)) {
            negatives++
            return null
        }
        lookups++
        val record = table.find(key)
        if (record == null) falsePositives++
        return record
    }

    fun output(): String = "READS $checks $negatives $lookups $falsePositives"
}
