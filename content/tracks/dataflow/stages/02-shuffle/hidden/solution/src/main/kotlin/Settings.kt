private const val SIZE_LIMIT = 1_000_000_000_000_000L
private const val NO_LIMIT = -1L

class Settings {
    var memoryLimit: Long? = null
        private set

    fun apply(tokens: List<String>) {
        if (tokens.size != 3) syntaxError()
        val value = tokens[2]
        when (tokens[1]) {
            "memory" -> memoryLimit = limitOrNone(value, 1L..SIZE_LIMIT)
            else -> fail(ErrorCode.UNKNOWN_SETTING)
        }
    }

    private fun limitOrNone(token: String, range: LongRange): Long? {
        val number = integerIn(token, NO_LIMIT..range.last) ?: syntaxError()
        return when (number) {
            NO_LIMIT -> null
            in range -> number
            else -> syntaxError()
        }
    }
}
