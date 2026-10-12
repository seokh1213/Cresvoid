private const val SIZE_LIMIT = 1_000_000_000_000_000L
private const val SKEW_FACTOR_LIMIT = 1000L
private const val NO_LIMIT = -1L

class Settings {
    var memoryLimit: Long? = null
        private set
    var broadcastThreshold: Long? = 10_485_760L
        private set
    var skewFactor = 5L
        private set
    var skewThreshold = 268_435_456L
        private set
    var advisorySize = 67_108_864L
        private set

    fun apply(tokens: List<String>) {
        if (tokens.size != 3) syntaxError()
        val value = tokens[2]
        when (tokens[1]) {
            "memory" -> memoryLimit = limitOrNone(value, 1L..SIZE_LIMIT)
            "broadcastThreshold" -> broadcastThreshold = limitOrNone(value, 0L..SIZE_LIMIT)
            "skewFactor" -> skewFactor = integerIn(value, 1L..SKEW_FACTOR_LIMIT) ?: syntaxError()
            "skewThreshold" -> skewThreshold = integerIn(value, 0L..SIZE_LIMIT) ?: syntaxError()
            "advisorySize" -> advisorySize = integerIn(value, 1L..SIZE_LIMIT) ?: syntaxError()
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
