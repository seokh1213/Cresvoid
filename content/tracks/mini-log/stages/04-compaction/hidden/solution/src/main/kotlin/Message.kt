class Message(val key: String?, val value: String?) {
    val isTombstone: Boolean get() = value == null
    val sizeBytes: Long get() = (key?.length ?: 0).toLong() + (value?.length ?: 0)
}
