data class RangeWindow(val end: Long, val length: Long) {
    val start: Long get() = end - length
}
