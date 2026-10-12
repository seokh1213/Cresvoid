const val NULL_KEY = 0

private const val KEY_HEADER_BYTES = 2
private const val INITIAL_CAPACITY = 1024
private const val UNKNOWN = -1

class KeyDictionary {
    private val texts = arrayListOf(NULL_TOKEN)
    private var hashes = IntArray(INITIAL_CAPACITY)
    private var keyBytes = IntArray(INITIAL_CAPACITY).also { it[NULL_KEY] = KEY_HEADER_BYTES }
    private var slots = IntArray(INITIAL_CAPACITY * 2) { UNKNOWN }
    private val prefixTables = arrayOfNulls<IntArray>(KEY_MAX_LENGTH + 1)
    private var keysInOrder = intArrayOf(NULL_KEY)
    private var ranks = intArrayOf(0)

    val size: Int get() = texts.size

    fun keyOf(text: String): Int = keyOf(text, 0, text.length)

    fun keyOf(text: String, start: Int, end: Int): Int {
        var hash = 0
        for (index in start until end) hash = 31 * hash + text[index].code
        val mask = slots.size - 1
        var slot = hash.mixed() and mask
        while (slots[slot] != UNKNOWN) {
            val key = slots[slot]
            if (hashes[key] == hash && texts[key].length == end - start && texts[key].regionMatches(0, text, start, end - start)) return key
            slot = (slot + 1) and mask
        }
        val key = register(text.substring(start, end), hash)
        slots[slot] = key
        if (texts.size * 2 > slots.size) rehash()
        return key
    }

    fun text(key: Int): String = texts[key]

    fun keyBytes(key: Int): Int = keyBytes[key]

    fun partitionOf(key: Int, partitions: Int): Int = if (key == NULL_KEY) 0 else hashes[key].mod(partitions)

    fun prefix(key: Int, length: Int): Int {
        if (key == NULL_KEY || texts[key].length <= length) return key
        val known = prefixTable(length)[key]
        if (known != UNKNOWN) return known
        val prefixKey = keyOf(texts[key], 0, length)
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

    private fun register(text: String, hash: Int): Int {
        val key = texts.size
        texts += text
        if (key == hashes.size) {
            hashes = hashes.copyOf(key * 2)
            keyBytes = keyBytes.copyOf(key * 2)
        }
        hashes[key] = hash
        keyBytes[key] = text.length + KEY_HEADER_BYTES
        return key
    }

    private fun rehash() {
        slots = IntArray(slots.size * 2) { UNKNOWN }
        val mask = slots.size - 1
        for (key in 1 until texts.size) {
            var slot = hashes[key].mixed() and mask
            while (slots[slot] != UNKNOWN) slot = (slot + 1) and mask
            slots[slot] = key
        }
    }

    // 이웃한 해시값도 표 전체에 흩어지도록 황금비에서 얻은 상수를 곱한다
    private fun Int.mixed(): Int = (this * -1640531527) xor (this ushr 16)

    private fun prefixTable(length: Int): IntArray {
        val table = prefixTables[length]
        if (table != null && table.size >= texts.size) return table
        val grown = IntArray(maxOf(texts.size * 2, INITIAL_CAPACITY)) { UNKNOWN }
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
