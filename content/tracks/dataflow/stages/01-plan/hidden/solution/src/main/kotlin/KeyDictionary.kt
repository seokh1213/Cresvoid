const val NULL_KEY = 0

private const val INITIAL_CAPACITY = 1024
private const val UNKNOWN_PREFIX = -1

class KeyDictionary {
    private val keysByText = HashMap<String, Int>()
    private val texts = arrayListOf(NULL_TOKEN)
    private var hashes = IntArray(INITIAL_CAPACITY)
    private val prefixTables = arrayOfNulls<IntArray>(KEY_MAX_LENGTH + 1)
    private var keysInOrder = intArrayOf(NULL_KEY)
    private var ranks = intArrayOf(0)

    val size: Int get() = texts.size

    fun keyOf(text: String): Int = keysByText.getOrPut(text) { register(text) }

    fun text(key: Int): String = texts[key]

    fun partitionOf(key: Int, partitions: Int): Int = if (key == NULL_KEY) 0 else hashes[key].mod(partitions)

    fun prefix(key: Int, length: Int): Int {
        if (key == NULL_KEY || texts[key].length <= length) return key
        val known = prefixTable(length)[key]
        if (known != UNKNOWN_PREFIX) return known
        val prefixKey = keyOf(texts[key].substring(0, length))
        prefixTable(length)[key] = prefixKey
        return prefixKey
    }

    fun ranks(): IntArray {
        if (keysInOrder.size < texts.size) mergeNewKeysIntoOrder()
        return ranks
    }

    fun sortInKeyOrder(keys: IntArray): IntArray {
        val ranks = ranks()
        val sortedRanks = IntArray(keys.size) { ranks[keys[it]] }
        sortedRanks.sort()
        return IntArray(sortedRanks.size) { keysInOrder[sortedRanks[it]] }
    }

    private fun register(text: String): Int {
        val key = texts.size
        texts += text
        if (key == hashes.size) hashes = hashes.copyOf(key * 2)
        hashes[key] = text.hashCode()
        return key
    }

    private fun prefixTable(length: Int): IntArray {
        val table = prefixTables[length]
        if (table != null && table.size >= texts.size) return table
        val grown = IntArray(maxOf(texts.size * 2, INITIAL_CAPACITY)) { UNKNOWN_PREFIX }
        table?.copyInto(grown)
        prefixTables[length] = grown
        return grown
    }

    private fun mergeNewKeysIntoOrder() {
        val newKeys = (keysInOrder.size until texts.size).sortedBy { texts[it] }
        val merged = IntArray(texts.size)
        var oldIndex = 0
        var newIndex = 0
        for (position in merged.indices) {
            val takeOld = newIndex == newKeys.size ||
                (oldIndex < keysInOrder.size && precedes(keysInOrder[oldIndex], newKeys[newIndex]))
            merged[position] = if (takeOld) keysInOrder[oldIndex++] else newKeys[newIndex++]
        }
        keysInOrder = merged
        ranks = IntArray(merged.size)
        merged.forEachIndexed { rank, key -> ranks[key] = rank }
    }

    private fun precedes(rankedKey: Int, newKey: Int): Boolean = rankedKey == NULL_KEY || texts[rankedKey] < texts[newKey]
}
