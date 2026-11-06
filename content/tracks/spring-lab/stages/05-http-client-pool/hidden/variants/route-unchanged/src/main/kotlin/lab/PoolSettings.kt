package lab

data class PoolSettings(
    val maxTotal: Int = 24,
    val maxPerRoute: Int = 5,
    val waitMillis: Long = 200,
)
