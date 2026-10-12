class Message(val key: String?, val value: String) {
    val sizeBytes: Long get() = (key?.length ?: 0).toLong() + value.length
}
