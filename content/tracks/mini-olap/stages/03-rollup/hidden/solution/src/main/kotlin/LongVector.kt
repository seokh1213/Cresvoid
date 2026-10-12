class LongVector(private val emptyValue: Long) {
    var values = LongArray(16)
        private set
    var size = 0
        private set

    fun appendEmpty() {
        if (size == values.size) values = values.copyOf(size + size / 2)
        values[size++] = emptyValue
    }
}
