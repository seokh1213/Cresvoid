package lab

data class PoolSettings(
    val maxTotal: Int = 24,
    val maxPerRoute: Int = 20,
    val waitMillis: Long = 200,
)
