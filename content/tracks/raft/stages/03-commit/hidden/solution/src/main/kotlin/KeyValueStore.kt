class KeyValueStore {
    private val values = HashMap<String, String>()

    fun apply(entry: LogEntry) {
        values[entry.key] = entry.value
    }

    fun valueOf(key: String): String? = values[key]
}
