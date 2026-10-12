private const val DIRECT_INDEX_LIMIT = 1L shl 23
private const val DIRECT_SLOTS_PER_KEY = 8L
private const val DIRECT_SLOTS_ALWAYS_AFFORDABLE = 1024L

sealed class KeyIndex {
    var size = 0
        protected set

    abstract fun idOf(key: Long): Int

    companion object {
        fun forKeySpace(keySpace: Long, keyCount: Int): KeyIndex {
            val affordable = minOf(DIRECT_INDEX_LIMIT, DIRECT_SLOTS_PER_KEY * keyCount + DIRECT_SLOTS_ALWAYS_AFFORDABLE)
            return if (keySpace <= affordable) DirectKeyIndex(keySpace.toInt()) else HashKeyIndex()
        }
    }
}

class DirectKeyIndex(keySpace: Int) : KeyIndex() {
    private val idsPlusOne = IntArray(keySpace)

    override fun idOf(key: Long): Int {
        val slot = key.toInt()
        val stored = idsPlusOne[slot]
        if (stored != 0) return stored - 1
        idsPlusOne[slot] = ++size
        return size - 1
    }
}

class HashKeyIndex : KeyIndex() {
    private var keys = LongArray(16)
    private var idsPlusOne = IntArray(16)

    override fun idOf(key: Long): Int {
        val mask = keys.size - 1
        var slot = spread(key) and mask
        while (true) {
            val stored = idsPlusOne[slot]
            if (stored == 0) break
            if (keys[slot] == key) return stored - 1
            slot = (slot + 1) and mask
        }
        keys[slot] = key
        idsPlusOne[slot] = ++size
        if (size * 2 > keys.size) grow()
        return size - 1
    }

    private fun grow() {
        val oldKeys = keys
        val oldIds = idsPlusOne
        keys = LongArray(oldKeys.size * 2)
        idsPlusOne = IntArray(oldKeys.size * 2)
        val mask = keys.size - 1
        for (index in oldKeys.indices) {
            if (oldIds[index] == 0) continue
            var slot = spread(oldKeys[index]) and mask
            while (idsPlusOne[slot] != 0) slot = (slot + 1) and mask
            keys[slot] = oldKeys[index]
            idsPlusOne[slot] = oldIds[index]
        }
    }

    // 이어진 키도 표 전체에 흩어지도록 황금비에서 얻은 상수를 곱한다
    private fun spread(key: Long): Int = ((key * -7046029254386353131L) ushr 32).toInt()
}
