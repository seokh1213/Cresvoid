class LongColumn {
    private var values = LongArray(4)
    var size = 0
        private set

    val last: Long get() = values[size - 1]

    operator fun get(index: Int): Long = values[index]

    fun append(value: Long) {
        if (size == values.size) values = values.copyOf(size * 2)
        values[size++] = value
    }

    // 값이 서로 다르고 오름차순일 때만 맞는 개수를 돌려준다
    fun countAtMost(limit: Long): Int {
        val found = values.binarySearch(limit, 0, size)
        return if (found >= 0) found + 1 else -(found + 1)
    }
}
